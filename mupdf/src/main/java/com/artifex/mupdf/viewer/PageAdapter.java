package com.artifex.mupdf.viewer;

import static com.xlk.mupdf.library.MupdfMacro.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.artifex.mupdf.util.Debugger;

public class PageAdapter extends BaseAdapter {
    private final Context mContext;
    private String mWaterMark = "";
    private float mScale = 1.0f;
    private final MuPDFCore mCore;
    private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();
    private Bitmap mSharedHqBm;

    public PageAdapter(Context c, MuPDFCore core) {
        mContext = c;
        mCore = core;
    }

    public PageAdapter(Context c, MuPDFCore core, float fullWidthScale, String watermark) {
        mContext = c;
        mCore = core;
        mScale = fullWidthScale;
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
        //  recycle and release the shared bitmap.
        if (mSharedHqBm != null)
            mSharedHqBm.recycle();
        mSharedHqBm = null;
    }

    public void refresh() {
        mPageSizes.clear();
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        final PageView pageView;
        if (convertView == null) {
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            if (mSharedHqBm != null) {
                Debugger.i(TAG, "getView: mSharedHqBm Size=" + mSharedHqBm.getWidth() + "," + mSharedHqBm.getHeight());
            }
            if (mSharedHqBm == null || mSharedHqBm.getWidth() != parentWidth || mSharedHqBm.getHeight() != parentHeight) {
                if (parentWidth > 0 && parentHeight > 0)
                    mSharedHqBm = Bitmap.createBitmap(parentWidth, parentHeight, Bitmap.Config.ARGB_8888);
                else
                    mSharedHqBm = null;
            }

            Point parentSize = new Point(parentWidth, parentHeight);
            Debugger.i(TAG, "getView: parentSize=" + parentSize);
            pageView = new PageView(mContext, mCore, parentSize, mSharedHqBm, mWaterMark);
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
                        Debugger.i(TAG, "PageAdapter.doInBackground: ");
                        return mCore.getPageSize(position);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(PointF result) {
                    int page = pageView.getPage();
                    Debugger.i(TAG, "PageAdapter.onPostExecute: result=" + result + ",page=" + page + ",position=" + position);
                    result = new PointF(result.x * mScale, result.y * mScale);
                    super.onPostExecute(result);
                    // We now know the page size
                    mPageSizes.put(position, result);
                    // 检查自我们开始以来，此视图尚未被其他页面重复使用
                    if (page == position)
                        pageView.setPage(position, result);
                }
            };

            sizingTask.execute((Void) null);
        }
        return pageView;
    }
}
