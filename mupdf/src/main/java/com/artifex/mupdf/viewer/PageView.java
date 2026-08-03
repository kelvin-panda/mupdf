package com.artifex.mupdf.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.util.Debugger;
import com.xlk.mupdf.library.MupdfMacro;
import com.xlk.mupdf.library.R;

/**
 * Make our ImageViews opaque to optimize redraw
 * 使我们的 ImageViews 不透明以优化重绘
 */
@SuppressLint("AppCompatCustomView")
class OpaqueImageView extends ImageView {
    private final TextPaint textPaint = new TextPaint();
    private String mWaterMark;

    public OpaqueImageView(Context context, String watermark) {
        super(context);
        if (watermark != null && !watermark.isEmpty()) {
            mWaterMark = watermark;
            textPaint.setTextSize(60);
            textPaint.setColor(Color.parseColor("#66F4511E"));
            textPaint.setAntiAlias(true);
            textPaint.setTextSkewX(-0.75f);//设置文本倾斜度，负值向左倾斜
        }
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mWaterMark != null && !mWaterMark.isEmpty()) {
            int width = getWidth();
            int height = getHeight();
            float textWidth = textPaint.measureText(mWaterMark);
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textHeight = fontMetrics.bottom - fontMetrics.top;

            // 计算文本的起始坐标
            float x = (width - textWidth) / 2;
            float y = (height + textHeight) / 2 - fontMetrics.bottom;

            canvas.drawText(mWaterMark, x, y, textPaint);
        }
    }
}

public class PageView extends ViewGroup {
    private static final String TAG = "PageView";
    private final MuPDFCore mCore;

    private static final int HIGHLIGHT_COLOR = 0x80cc6600;
    private static final int LINK_COLOR = 0x800066cc;
    private static final int BOX_COLOR = 0xFF4444FF;
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_DIALOG_DELAY = 200;
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final long MAX_BITMAP_BYTES = 64L * 1024L * 1024L;

    protected final Context mContext;
    private final String mWaterMark;

    protected int mPageNumber;
    /**
     * 设备宽高
     */
    private Point mParentSize;
    /**
     * 最小缩放时的页面大小
     */
    protected Point mSize;
    /**
     * 计算适合屏幕限制的缩放尺寸这是最小缩放时的尺寸
     */
    protected float mSourceScale;

    private ImageView mEntire; // 以最小缩放比例渲染的图像
    private Bitmap mEntireBm;
    private Matrix mEntireMat;
    private AsyncTask<Void, Void, Link[]> mGetLinkInfo;
    private CancellableAsyncTask<Void, Boolean> mDrawEntire;
    /**
     * 渲染任务进行中又收到渲染请求时置为 true，当前任务结束后自动补渲染，避免漏更新
     */
    private boolean mRenderPending;

    private Quad mSearchBoxes[][];
    protected Link mLinks[];
    private View mSearchView;
    private boolean mIsBlank;
    private boolean mHighlightLinks;

    private ImageView mErrorIndicator;

    private ProgressBar mBusyIndicator;
    private final Handler mHandler = new Handler();
    private int mRenderedWidth;
    private int mRenderedHeight;
    private int mRenderedPage = -1;

    public PageView(Context c, MuPDFCore core, Point parentSize, String watermark) {
        super(c);
        mContext = c;
        mCore = core;
        mParentSize = safePoint(parentSize);
        setBackgroundColor(MupdfMacro.backgroundColor);
        mEntireBm = createBitmapSafely(mParentSize.x, mParentSize.y);
        mEntireMat = new Matrix();
        mWaterMark = watermark;
    }

    public Bitmap getEntireBm() {
        if (mEntireBm == null || mEntireBm.isRecycled())
            return null;
        return Bitmap.createBitmap(mEntireBm);
    }

    private Point safePoint(Point point) {
        if (point == null) {
            return new Point(1, 1);
        }
        return new Point(Math.max(1, point.x), Math.max(1, point.y));
    }

    private Bitmap createBitmapSafely(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        try {
            return Bitmap.createBitmap(safeWidth, safeHeight, Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Debugger.e(TAG, Debugger.getFullStackTrace(e));
            return null;
        } catch (IllegalArgumentException e) {
            Debugger.e(TAG, e);
            return null;
        }
    }

    private void clearEntireBitmap(boolean clearImage) {
        if (clearImage && mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }
        if (mEntireBm != null && !mEntireBm.isRecycled()) {
            mEntireBm.recycle();
        }
        mEntireBm = null;
        mRenderedWidth = 0;
        mRenderedHeight = 0;
        mRenderedPage = -1;
    }

    private boolean ensureEntireBitmap() {
        if (mEntireBm != null && !mEntireBm.isRecycled())
            return true;
        mEntireBm = createBitmapSafely(mParentSize.x, mParentSize.y);
        return mEntireBm != null && !mEntireBm.isRecycled();
    }

    private boolean ensureEntireBitmap(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (mEntireBm != null && !mEntireBm.isRecycled()
                && mEntireBm.getWidth() == safeWidth && mEntireBm.getHeight() == safeHeight)
            return true;
        clearEntireBitmap(true);
        mEntireBm = createBitmapSafely(safeWidth, safeHeight);
        return mEntireBm != null && !mEntireBm.isRecycled();
    }

    private Point limitBitmapSize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        double scale = 1.0;
        scale = Math.min(scale, (double) MAX_BITMAP_DIMENSION / safeWidth);
        scale = Math.min(scale, (double) MAX_BITMAP_DIMENSION / safeHeight);
        long bytes = (long) safeWidth * (long) safeHeight * 4L;
        if (bytes > MAX_BITMAP_BYTES) {
            scale = Math.min(scale, Math.sqrt((double) MAX_BITMAP_BYTES / bytes));
        }
        if (scale < 1.0) {
            safeWidth = Math.max(1, (int) Math.floor(safeWidth * scale));
            safeHeight = Math.max(1, (int) Math.floor(safeHeight * scale));
        }
        return new Point(safeWidth, safeHeight);
    }

    private void reinit() {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel();
            mDrawEntire = null;
        }

        if (mGetLinkInfo != null) {
            mGetLinkInfo.cancel(true);
            mGetLinkInfo = null;
        }

        mIsBlank = true;
        mPageNumber = 0;

        if (mSize == null)
            mSize = mParentSize;

        if (mEntire != null) mEntire.invalidate();

        mSearchBoxes = null;
        mLinks = null;

        clearRenderError();
    }

    public void releaseResources() {
        reinit();

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
        clearRenderError();
    }

    public void releaseBitmaps() {
        reinit();

        // 清除 ImageView 引用，防止显示已回收的 Bitmap
        if (mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }
        // recycle bitmaps before releasing them.

        clearEntireBitmap(true);
    }

    public void blank(int page) {
        reinit();
        mPageNumber = page;

        // blank 需要彻底清空上一页的内容
        if (mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }
        mRenderedWidth = 0;
        mRenderedHeight = 0;
        mRenderedPage = -1;
        if (mBusyIndicator == null) {
            mBusyIndicator = new ProgressBar(mContext);
            mBusyIndicator.setIndeterminate(true);
            addView(mBusyIndicator);
        }

        setBackgroundColor(MupdfMacro.backgroundColor);
    }

    protected void clearRenderError() {
        if (mErrorIndicator == null)
            return;

        removeView(mErrorIndicator);
        mErrorIndicator = null;
        invalidate();
    }

    protected void setRenderError(String why) {
        Debugger.i(TAG, "setRenderError: " + why);
        int page = mPageNumber;
        reinit();
        mPageNumber = page;

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
        if (mSearchView != null) {
            removeView(mSearchView);
            mSearchView = null;
        }

        if (mErrorIndicator == null) {
            mErrorIndicator = new OpaqueImageView(mContext, "");
            mErrorIndicator.setScaleType(ImageView.ScaleType.CENTER);
            addView(mErrorIndicator);
            Drawable mErrorIcon = getResources().getDrawable(R.drawable.ic_error_red_24dp);
            mErrorIndicator.setImageDrawable(mErrorIcon);
            mErrorIndicator.setBackgroundColor(BACKGROUND_COLOR);
        }

        setBackgroundColor(Color.TRANSPARENT);
        mErrorIndicator.bringToFront();
        mErrorIndicator.invalidate();
    }

    /**
     * 根据{@link MupdfMacro#clarityLimitMode}限制宽高与清晰度
     */
    public float maxSourceScale(PointF size) {
        Debugger.d(TAG, "maxSourceScale MupdfMacro.clarityLimitMode:" + MupdfMacro.clarityLimitMode);
        if (MupdfMacro.clarityLimitMode >= 0) {
            // 不同级别的分辨率限制
            final int[] RESOLUTION_LIMITS = {
                    7680, 4320, // 8K
                    3840, 2160, // 4K
                    2560, 1440, // 2K
                    1920, 1080, // 1080p
                    1280, 720   // 720p
            };
            // 选择要限制的分辨率级别（例如4K级别，索引2-3）
            int limitMode = Math.max(0, Math.min(MupdfMacro.clarityLimitMode, RESOLUTION_LIMITS.length / 2 - 1));
            int limitIndex = limitMode * 2; // 0:8K, 2:4K, 4:2K, 6:1080p, 8:720p

            int maxWidth = RESOLUTION_LIMITS[limitIndex];
            int maxHeight = RESOLUTION_LIMITS[limitIndex + 1];

            return Math.min(maxWidth / size.x, maxHeight / size.y);
        }
        return Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
    }

    public void setPage(int page, PointF size) {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel();
            mDrawEntire = null;
        }

        mIsBlank = false;
        // Highlights may be missing because mIsBlank was true on last draw
        if (mSearchView != null)
            mSearchView.invalidate();

        mPageNumber = page;

        if (size == null) {
            setRenderError("Error loading page");
            size = new PointF(612, 792);
        }
        //计算缩放因子
        mSourceScale = maxSourceScale(size);
//        // 计算适合屏幕限制的缩放尺寸这是最小缩放时的尺寸
//        mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
//        if (mSourceScale > maxSourceScale) {
//            Debugger.d(TAG, "setPage: 缩放因子超出了 mSourceScale：" + mSourceScale + ",maxSourceScale=" + maxSourceScale);
//            mSourceScale = maxSourceScale;
//        }
        Point newSize = new Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));
        mSize = newSize;
        Debugger.i(TAG, "setPage: page=" + page + ",size=" + size + ",mParentSize=" + mParentSize + ",mSourceScale=" + mSourceScale + ",mSize=" + mSize);

        if (mErrorIndicator != null)
            return;

        if (!ensureEntireBitmap(mSize.x, mSize.y)) {
            setRenderError("Error allocating page bitmap");
            return;
        }

        if (mEntire == null) {
            mEntire = new OpaqueImageView(mContext, mWaterMark);
            mEntire.setScaleType(ImageView.ScaleType.MATRIX);
            addView(mEntire);
        }
        // PageView instances are recycled by ReaderView. Reapply the current
        // filter whenever a cached view is assigned to another page.
        applyColorFilter(mEntire);

        if (mRenderedPage != page) {
            mEntire.setImageBitmap(null);
        }
        mEntire.invalidate();

        // 在后台获取链接信息
        /*mGetLinkInfo = new AsyncTask<Void, Void, Link[]>() {
            protected Link[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(Link[] v) {
                mLinks = v;
                if (mSearchView != null)
                    mSearchView.invalidate();
            }
        };

        mGetLinkInfo.execute();*/

        Debugger.d(TAG, "调用 renderEntirePage setPage page=" + page);
        renderEntirePage(mSize.x, mSize.y, false);

        if (mSearchView == null) {
            mSearchView = new View(mContext) {
                @Override
                protected void onDraw(final Canvas canvas) {
                    super.onDraw(canvas);
                    // Work out current total scale factor
                    // from source to view
                    final float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
                    final Paint paint = new Paint();
                    paint.setStyle(Paint.Style.FILL);

                    if (!mIsBlank && mSearchBoxes != null) {
                        paint.setColor(HIGHLIGHT_COLOR);
                        for (Quad[] searchBox : mSearchBoxes) {
                            for (Quad q : searchBox) {
                                Path path = new Path();
                                path.moveTo(q.ul_x * scale, q.ul_y * scale);
                                path.lineTo(q.ll_x * scale, q.ll_y * scale);
                                path.lineTo(q.lr_x * scale, q.lr_y * scale);
                                path.lineTo(q.ur_x * scale, q.ur_y * scale);
                                path.close();
                                canvas.drawPath(path, paint);
                            }
                        }
                    }

                    if (!mIsBlank && mLinks != null && mHighlightLinks) {
                        paint.setColor(LINK_COLOR);
                        for (Link link : mLinks)
                            canvas.drawRect(link.getBounds().x0 * scale, link.getBounds().y0 * scale,
                                    link.getBounds().x1 * scale, link.getBounds().y1 * scale,
                                    paint);
                    }
                }
            };

            addView(mSearchView);
        }

        Debugger.d(TAG, "setPage 调用 requestLayout page=" + page);
        requestLayout();
    }

    private void renderEntirePage(final int width, final int height, final boolean update) {
        if (mIsBlank || width <= 0 || height <= 0)
            return;

        if (mDrawEntire != null) {
            // 已有渲染任务在运行：记下待渲染标记，当前任务结束后自动补渲染，避免漏更新
            mRenderPending = true;
            return;
        }

        Point renderSize = limitBitmapSize(width, height);
        final boolean quietUpdate = update
                && mEntire != null
                && mEntire.getDrawable() != null
                && mEntireBm != null
                && !mEntireBm.isRecycled()
                && mRenderedPage == mPageNumber;
        final Bitmap renderBitmap;
        if (quietUpdate) {
            renderBitmap = createBitmapSafely(renderSize.x, renderSize.y);
            if (renderBitmap == null || renderBitmap.isRecycled()) {
                setRenderError("Error allocating page bitmap");
                return;
            }
        } else {
            if (!ensureEntireBitmap(renderSize.x, renderSize.y)) {
                setRenderError("Error allocating page bitmap");
                return;
            }
            renderBitmap = mEntireBm;
        }

        final int renderPage = mPageNumber;
        final int targetW = width;
        final int targetH = height;
        final int renderW = renderSize.x;
        final int renderH = renderSize.y;
        final boolean swapBitmap = renderBitmap != mEntireBm;
        final boolean[] bitmapAdopted = {!swapBitmap};
        final CancellableTaskDefinition<Void, Boolean> renderTask = update
                ? getUpdatePageTask(renderBitmap, renderPage, renderW, renderH, 0, 0, renderW, renderH)
                : getDrawPageTask(renderBitmap, renderPage, renderW, renderH, 0, 0, renderW, renderH);
        CancellableTaskDefinition<Void, Boolean> task = new CancellableTaskDefinition<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Void... params) {
                return renderTask.doInBackground(params);
            }

            @Override
            public void doCancel() {
                renderTask.doCancel();
            }

            @Override
            public void doCleanup() {
                renderTask.doCleanup();
                if (!bitmapAdopted[0] && renderBitmap != null && !renderBitmap.isRecycled()) {
                    renderBitmap.recycle();
                }
            }
        };

        mDrawEntire = new CancellableAsyncTask<Void, Boolean>(task) {
            @Override
            public void onPreExecute() {
                if (!quietUpdate) {
                    setBackgroundColor(MupdfMacro.backgroundColor);
                    if (mEntire != null)
                        mEntire.invalidate();

                    if (mBusyIndicator == null) {
                        mBusyIndicator = new ProgressBar(mContext);
                        mBusyIndicator.setIndeterminate(true);
                        addView(mBusyIndicator);
                        mBusyIndicator.setVisibility(INVISIBLE);
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                if (mBusyIndicator != null)
                                    mBusyIndicator.setVisibility(VISIBLE);
                            }
                        }, PROGRESS_DIALOG_DELAY);
                    }
                }
            }

            @Override
            public void onPostExecute(Boolean result) {
                mDrawEntire = null;
                if (mBusyIndicator != null)
                    removeView(mBusyIndicator);
                mBusyIndicator = null;

                if (mPageNumber != renderPage) {
                    mRenderPending = false;
                    return;
                }

                if (result.booleanValue()) {
                    clearRenderError();
                    int viewW = getWidth();
                    int viewH = getHeight();
                    if (viewW > 0 && viewH > 0 && (viewW != targetW || viewH != targetH)) {
                        renderEntirePage(viewW, viewH, false);
                        return;
                    }
                    mRenderedWidth = targetW;
                    mRenderedHeight = targetH;
                    mRenderedPage = renderPage;
                    Bitmap oldBitmap = null;
                    if (swapBitmap) {
                        oldBitmap = mEntireBm;
                        mEntireBm = renderBitmap;
                        bitmapAdopted[0] = true;
                    }
                    if (mEntire != null && mEntireBm != null && !mEntireBm.isRecycled()) {
                        mEntireMat.setScale(getWidth() / (float) mEntireBm.getWidth(), getHeight() / (float) mEntireBm.getHeight());
                        mEntire.setImageMatrix(mEntireMat);
                        mEntire.setImageBitmap(mEntireBm);
                        mEntire.invalidate();
                    }
                    if (oldBitmap != null && oldBitmap != mEntireBm && !oldBitmap.isRecycled()) {
                        oldBitmap.recycle();
                    }
                } else {
                    setRenderError(update ? "Error updating page" : "Error rendering page");
                }
                setBackgroundColor(Color.TRANSPARENT);
                // 若期间有新的渲染请求被跳过，自动补一次渲染
                if (mRenderPending) {
                    mRenderPending = false;
                    int vw = getWidth() > 0 ? getWidth() : mSize.x;
                    int vh = getHeight() > 0 ? getHeight() : mSize.y;
                    if (vw > 0 && vh > 0) {
                        renderEntirePage(vw, vh, true);
                    }
                }
            }
        };

        mDrawEntire.execute();
    }

    public void setSearchBoxes(Quad searchBoxes[][]) {
        mSearchBoxes = searchBoxes;
        if (mSearchView != null)
            mSearchView.invalidate();
    }

    public void setLinkHighlighting(boolean f) {
        mHighlightLinks = f;
        if (mSearchView != null)
            mSearchView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int x, y;
        switch (View.MeasureSpec.getMode(widthMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                x = (int) mSize.x;
                break;
            default:
                x = View.MeasureSpec.getSize(widthMeasureSpec);
        }
        switch (View.MeasureSpec.getMode(heightMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                y = (int) mSize.y;
                break;
            default:
                y = View.MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(x, y);

        if (mBusyIndicator != null) {
            int limit = (int) (Math.min(mParentSize.x, mParentSize.y) / 2);
            mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }
        if (mErrorIndicator != null) {
            int limit = (int) (Math.min(mParentSize.x, mParentSize.y) / 2);
            mErrorIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        Debugger.i(TAG, "onLayout: changed:" + changed + ",left:" + left + ",top:" + top + ",right:" + right + ",bottom:" + bottom + ",w:" + w + ",h:" + h);
        if (mEntire != null) {
            Debugger.i(TAG, "onLayout: mEntire:" + mEntire.getWidth() + " x " + mEntire.getHeight() + ",mSize:" + mSize);
            int bitmapW = mEntireBm != null && !mEntireBm.isRecycled() ? mEntireBm.getWidth() : mSize.x;
            int bitmapH = mEntireBm != null && !mEntireBm.isRecycled() ? mEntireBm.getHeight() : mSize.y;
            if (bitmapW > 0 && bitmapH > 0) {
                mEntireMat.setScale(w / (float) bitmapW, h / (float) bitmapH);
                mEntire.setImageMatrix(mEntireMat);
                mEntire.invalidate();
            }
            mEntire.layout(0, 0, w, h);
        }

        if (mSearchView != null) {
            mSearchView.layout(0, 0, w, h);
        }

        if (!mIsBlank && mErrorIndicator == null && w > 0 && h > 0
                && (mRenderedWidth != w || mRenderedHeight != h)) {
            renderEntirePage(w, h, false);
        }

        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();

            mBusyIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }

        if (mErrorIndicator != null) {
            int bw = (int) (8.5 * mErrorIndicator.getMeasuredWidth());
            int bh = (int) (11 * mErrorIndicator.getMeasuredHeight());
            mErrorIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }
    }

    public void update() {
        Debugger.d(TAG, "update: ");
        int width = getWidth() > 0 ? getWidth() : mSize.x;
        int height = getHeight() > 0 ? getHeight() : mSize.y;
        renderEntirePage(width, height, true);
    }

    public int getPage() {
        return mPageNumber;
    }

    public boolean isBlankPage() {
        return mIsBlank;
    }

    /**
     * 将背景颜色（纸张色）作为颜色滤镜应用到渲染图像上。
     * 默认白底时 {@link MupdfMacro#buildColorFilter()} 返回 null，即不做处理。
     */
    private void applyColorFilter(ImageView imageView) {
        if (imageView != null) {
            imageView.setColorFilter(MupdfMacro.buildColorFilter());
        }
    }

    /**
     * 运行时刷新背景颜色设置。修改 {@link MupdfMacro#backgroundColor} 后调用即可立即生效。
     */
    public void refreshColorFilter() {
        applyColorFilter(mEntire);
        setBackgroundColor(MupdfMacro.backgroundColor);
        if (mEntire != null) mEntire.invalidate();
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public int hitLink(Link link) {
        if (link.isExternal()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.getURI()));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET); // API>=21: FLAG_ACTIVITY_NEW_DOCUMENT
            try {
                mContext.startActivity(intent);
            } catch (Exception x) {
                Debugger.e(TAG, x.toString());
//                Toast.makeText(getContext(), "Android does not allow following file:// link: " + link.getURI(), Toast.LENGTH_LONG).show();
            } catch (Throwable x) {
                Debugger.e(TAG, x.toString());
//                Toast.makeText(getContext(), x.getMessage(), Toast.LENGTH_LONG).show();
            }
            return 0;
        } else {
            return mCore.resolveLink(link);
        }
    }

    public int hitLink(float x, float y) {
        // Since link highlighting was implemented, the super class
        // PageView has had sufficient information to be able to
        // perform this method directly. Making that change would
        // make MuPDFCore.hitLinkPage superfluous.
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        float docRelX = (x - getLeft()) / scale;
        float docRelY = (y - getTop()) / scale;

        if (mLinks != null)
            for (Link l : mLinks)
                if (l.getBounds().contains(docRelX, docRelY))
                    return hitLink(l);
        return 0;
    }

    protected CancellableTaskDefinition<Void, Boolean> getDrawPageTask(final Bitmap bm, final int pageNumber,
                                                                       final int sizeX, final int sizeY,
                                                                       final int patchX, final int patchY, final int patchWidth, final int patchHeight) {
        final int taskPageNumber = pageNumber;
        return new MuPDFCancellableTaskDefinition<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Cookie cookie, Void... params) {
                if (bm == null || bm.isRecycled() || sizeX <= 0 || sizeY <= 0 || patchWidth <= 0 || patchHeight <= 0)
                    return new Boolean(false);
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                    bm.eraseColor(0);
                try {
                    Debugger.i(TAG, "getDrawPageTask doInBackground start mCore.drawPage page:" + taskPageNumber);
                    mCore.drawPage(bm, taskPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
                    Debugger.i(TAG, "getDrawPageTask doInBackground end mCore.drawPage page:" + taskPageNumber);
                    return new Boolean(true);
                } catch (Throwable e) {
                    Debugger.e(TAG, Debugger.getFullStackTrace(e));
                    return new Boolean(false);
                }
            }
        };

    }

    protected CancellableTaskDefinition<Void, Boolean> getUpdatePageTask(final Bitmap bm, final int pageNumber,
                                                                         final int sizeX, final int sizeY,
                                                                         final int patchX, final int patchY, final int patchWidth, final int patchHeight) {
        final int taskPageNumber = pageNumber;
        return new MuPDFCancellableTaskDefinition<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Cookie cookie, Void... params) {
                if (bm == null || bm.isRecycled() || sizeX <= 0 || sizeY <= 0 || patchWidth <= 0 || patchHeight <= 0)
                    return new Boolean(false);
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                    bm.eraseColor(0);
                try {
                    Debugger.i(TAG, "getUpdatePageTask doInBackground updatePage start");
                    mCore.updatePage(bm, taskPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
                    Debugger.i(TAG, "getUpdatePageTask doInBackground updatePage end");
                    return new Boolean(true);
                } catch (Throwable e) {
                    Debugger.e(TAG, Debugger.getFullStackTrace(e));
                    return new Boolean(false);
                }
            }
        };
    }

    protected Link[] getLinkInfo() {
        try {
            return mCore.getPageLinks(mPageNumber);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
