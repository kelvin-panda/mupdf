package com.xlk.mupdf.library;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
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

import com.artifex.mupdf.viewer.MuPDFCore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author : Administrator
 * created on 2026/6/3 14:32
 */
public class ThumbnailAdapter extends RecyclerView.Adapter<ThumbnailAdapter.ViewHolder> {

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
        this.executorService = Executors.newFixedThreadPool(4); // 限制线程池大小

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

    private Bitmap generateThumbnail(int pageNum) {
        try {
            PointF pageSize = core.getPageSize(pageNum);
            float scale = Math.min((float) thumbnailWidth / pageSize.x, (float) thumbnailHeight / pageSize.y);
            int renderWidth = Math.round(pageSize.x * scale);
            int renderHeight = Math.round(pageSize.y * scale);

            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            core.drawPage(bitmap, pageNum, renderWidth, renderHeight, 0, 0, renderWidth, renderHeight, null);
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
