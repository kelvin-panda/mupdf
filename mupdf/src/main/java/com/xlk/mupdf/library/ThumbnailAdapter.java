package com.xlk.mupdf.library;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.mupdf.util.Debugger;
import com.artifex.mupdf.viewer.MuPDFCore;
import com.artifex.mupdf.viewer.RenderScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author : Administrator
 * created on 2026/6/3 14:32
 */
public class ThumbnailAdapter extends RecyclerView.Adapter<ThumbnailAdapter.ViewHolder> {

    private static final String TAG = "ThumbnailAdapter";
    /** 页渲染排队时，缩略图让路的复查间隔 */
    private static final long PAGE_RENDER_YIELD_INTERVAL_MS = 50L;
    /** 让路最长等待：超时后照常生成，避免缩略图永远出不来 */
    private static final long PAGE_RENDER_YIELD_TIMEOUT_MS = 3000L;
    /** 在页面渲染线程上绘制缩略图的最长等待（避免阻塞缩略图线程过久） */
    private static final long THUMB_DRAW_TIMEOUT_MS = 15000L;

    private int currentPage = 0;          // 当前正在阅读的页码
    private final Context context;
    private final MuPDFCore core;
    private final int thumbnailWidth;
    private final int thumbnailHeight;
    private final LruCache<Integer, Bitmap> thumbnailCache; // 内存缓存
    private final ExecutorService executorService; // 用于后台生成缩略图
    private OnThumbnailClickListener listener;

    public ThumbnailAdapter(Context context, MuPDFCore core, int thumbWidth, int thumbHeight) {
        this.context = context;
        this.core = core;
        this.thumbnailWidth = thumbWidth;
        this.thumbnailHeight = thumbHeight;
        // MuPDFCore 的渲染本身就是"一把锁 + 串行"，多线程只会互相抢锁；
        // 单线程 + 让路（见 awaitPageRendersIdle）才能真正把优先级让给页面渲染
        this.executorService = Executors.newSingleThreadExecutor();

        // 设置缓存大小，例如最大为可用内存的1/8
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        thumbnailCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int position);
    }

    public void setOnThumbnailClickListener(OnThumbnailClickListener listener) {
        this.listener = listener;
    }

    public void setCurrentPage(int page) {
        int previous = currentPage;
        currentPage = page;
        notifyItemChanged(previous);
        notifyItemChanged(currentPage);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_thumbnail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // 检查缓存中是否有该页的缩略图
        Bitmap bitmap = thumbnailCache.get(position);
        if (bitmap != null && !bitmap.isRecycled()) {
            holder.imageView.setImageBitmap(bitmap);
        } else {
            // 若缓存没有，则在后台线程中生成
            loadThumbnailAsync(holder, position);
        }
        if (bitmap != null) {
            holder.imageView.setImageBitmap(bitmap);
        } else {
            holder.imageView.setImageResource(R.drawable.ic_mupdf_thumbnail_placeholder);
        }
        holder.pageNumber.setText(String.valueOf(position + 1));

        // 高亮当前页：加边框或改变背景（这里使用边框更接近PPT）
        if (position == currentPage) {
            holder.cardView.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.card_selected_bg));
        } else {
            holder.cardView.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.card_default_bg));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onThumbnailClick(position);
            }
        });
    }

    private void loadThumbnailAsync(ViewHolder holder, int pageNum) {
        // 给 ViewHolder 设置一个标志，防止在快速滚动时加载错位
        final int currentPosition = holder.getAdapterPosition();
        if (currentPosition == RecyclerView.NO_POSITION) return;

        executorService.submit(() -> {
            // 页面渲染排队/进行中时先让路：抢锁会让正在翻页的用户多等一整页的渲染时间
            awaitPageRendersIdle(pageNum);
            Bitmap thumbnail = generateThumbnail(pageNum);
            if (thumbnail != null) {
                // 将生成的缩略图存入缓存
                thumbnailCache.put(pageNum, thumbnail);
                // 回到 UI 线程更新视图
                ((Activity) context).runOnUiThread(() -> {
                    if (holder.getAdapterPosition() == currentPosition) {
                        holder.imageView.setImageBitmap(thumbnail);
                    }
                });
            }
        });
    }

    /**
     * 页面渲染排队/进行中时暂停缩略图预取。
     *
     * <p>{@link MuPDFCore#drawPage} 是同步方法，缩略图一渲染就会占住锁，正在排队的页面渲染
     * 必须等它跑完（实测 115MB 文档单张缩略图 0.65~0.86s）。这里在生成前让路，
     * 最多等 {@link #PAGE_RENDER_YIELD_TIMEOUT_MS}，超时后照常生成，避免缩略图永远出不来。</p>
     */
    private void awaitPageRendersIdle(int pageNum) {
        if (core == null || !core.isPageRenderBusy()) return;
        Debugger.i(TAG, "[Thumb] 页面渲染排队中（" + core.pendingPageRenderCount()
                + "），缩略图让路 page=" + (pageNum + 1));
        long deadline = SystemClock.uptimeMillis() + PAGE_RENDER_YIELD_TIMEOUT_MS;
        while (core.isPageRenderBusy()) {
            if (Thread.currentThread().isInterrupted()) return;
            if (SystemClock.uptimeMillis() >= deadline) {
                Debugger.i(TAG, "[Thumb] 等待超时，继续生成缩略图 page=" + (pageNum + 1)
                        + "（仍有 " + core.pendingPageRenderCount() + " 个页渲染排队）");
                return;
            }
            SystemClock.sleep(PAGE_RENDER_YIELD_INTERVAL_MS);
        }
        Debugger.i(TAG, "[Thumb] 页面渲染空闲，生成缩略图 page=" + (pageNum + 1));
    }

    private Bitmap generateThumbnail(int pageNum) {        try {
            PointF pageSize = core.getPageSize(pageNum);
            float scale = Math.min((float) thumbnailWidth / pageSize.x, (float) thumbnailHeight / pageSize.y);
            int renderWidth = Math.round(pageSize.x * scale);
            int renderHeight = Math.round(pageSize.y * scale);

            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            // 以低优先级提交到"页面渲染线程"：显示列表缓存按线程归属，同线程才能被后续打开该页复用；
            // 低优先级保证缩略图永远排在可见页/补高清之后。
            RenderScheduler.Task drawTask =
                    RenderScheduler.get().submit(() ->
                            core.drawPage(bitmap, pageNum, renderWidth, renderHeight,
                                    0, 0, renderWidth, renderHeight, null, true), false);
            if (!drawTask.await(THUMB_DRAW_TIMEOUT_MS)) {
                Debugger.i(TAG, "[Thumb] 在渲染线程上绘制超时 page=" + (pageNum + 1));
                return null;
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return core.countPages();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView imageView;
        TextView pageNumber;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            imageView = itemView.findViewById(R.id.thumbnail_image);
            pageNumber = itemView.findViewById(R.id.page_number);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public void clearCache() {
        thumbnailCache.evictAll();
    }

    public void shutdownExecutor() {
        executorService.shutdownNow();
    }
}
