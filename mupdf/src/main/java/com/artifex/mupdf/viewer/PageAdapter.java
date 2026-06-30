package com.artifex.mupdf.viewer;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.artifex.mupdf.util.Debugger;
public class PageAdapter extends BaseAdapter {
    private static final String TAG = "PageAdapter";
    private final Context mContext;
    private String mWaterMark = "";
    private final MuPDFCore mCore;
    private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();

    public PageAdapter(Context c, MuPDFCore core) {
        mContext = c;
        mCore = core;
    }

    public PageAdapter(Context c, MuPDFCore core, float fullWidthScale, String watermark) {
        mContext = c;
        mCore = core;
        mWaterMark = watermark;
    }

    public int getCount() {
        try {
            return mCore.countPages();
        } catch (RuntimeException e) {
            return 0;
        }
    }


    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    public void releaseBitmaps() {
    }

    public void refresh() {
        mPageSizes.clear();
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        final PageView pageView;
        if (convertView == null) {
            Point point = resolveParentSize(parent);
            int parentWidth = point.x;
            int parentHeight = point.y;
            Point parentSize = new Point(parentWidth, parentHeight);
            Debugger.i(TAG, "getView: parentSize=" + parentSize);
            pageView = new PageView(mContext, mCore, parentSize, mWaterMark);
        } else {
            pageView = (PageView) convertView;
        }

        PointF pageSize = mPageSizes.get(position);
        Debugger.i(TAG, "getView: position=" + position + ",pageSize=" + pageSize);
        if (pageSize != null) {
            // 我们已经知道页面大小。
            // 立即设置
            pageView.setPage(position, pageSize);
        } else {
            // 页面大小尚不清楚。暂时空白，并启动一个后台任务来查找尺寸
            pageView.blank(position);
            AsyncTask<Void, Void, PointF> sizingTask = new AsyncTask<Void, Void, PointF>() {
                @Override
                protected PointF doInBackground(Void... arg0) {
                    try {
                        Debugger.i(TAG, "getView doInBackground: ");
                        return mCore.getPageSize(position);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(PointF result) {
                    int page = pageView.getPage();
                    Debugger.i(TAG, "getView onPostExecute: result=" + result + ",page=" + page + ",position=" + position);
                    // 注意：这里不能再乘以 fullWidthScale(mScale)。
                    // setPage 与 mSearchView 的高亮坐标都假定传给它的 size 就是 MuPDFCore.getPageSize
                    // 返回的原始页面尺寸(72dpi 点)，预先缩放会破坏搜索高亮 Quad 与文字的精确对齐。
                    // 页面默认撑满全宽由 ReaderView.measureView 的 mScale 独立负责，无需在此提前缩放。
                    super.onPostExecute(result);
                    // We now know the page size
                    if (result != null) {
                        mPageSizes.put(position, result);
                    }
                    // 检查自我们开始以来，此视图尚未被其他页面重复使用
                    if (page == position)
                        pageView.setPage(position, result);
                }
            };

            sizingTask.execute((Void) null);
        }
        return pageView;
    }

    private Point resolveParentSize(ViewGroup parent) {
        int parentWidth = parent != null ? parent.getWidth() : 0;
        int parentHeight = parent != null ? parent.getHeight() : 0;
        if (parentWidth <= 0 || parentHeight <= 0) {
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            if (parentWidth <= 0) parentWidth = dm.widthPixels;
            if (parentHeight <= 0) parentHeight = dm.heightPixels;
        }
        return new Point(Math.max(1, parentWidth), Math.max(1, parentHeight));
    }

    /** 同步获取页面原始尺寸(72dpi)，用于连续拼页位置估算。已在 mPageSizes 缓存则直接返回。 */
    public PointF getPageSizeSync(int position) {
        PointF size = mPageSizes.get(position);
        if (size == null) {
            try {
                size = mCore.getPageSize(position);
            } catch (Exception e) {
                size = null;
            }
            if (size == null) size = new PointF(612, 792);
            mPageSizes.put(position, size);
        }
        return size;
    }
}
