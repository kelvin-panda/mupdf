package com.artifex.mupdf.viewer;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;

import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.util.Debugger;
import com.xlk.mupdf.library.R;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Stack;

public class ReaderView
        extends AdapterView<Adapter>
        implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener, Runnable {
    private static final String TAG = "ReaderView";
    private Context mContext;
    private boolean mLinksEnabled = false;
    private boolean tapDisabled = false;
    private int tapPageMargin;

    private static final int MOVING_DIAGONALLY = 0;
    private static final int MOVING_LEFT = 1;
    private static final int MOVING_RIGHT = 2;
    private static final int MOVING_UP = 3;
    private static final int MOVING_DOWN = 4;

    private static final int FLING_MARGIN = 100;
    private static final int GAP = 20;

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 64.0f;

    private static final boolean HORIZONTAL_SCROLLING = false;

    private PageAdapter mAdapter;
    public int mCurrent;    // Adapter's index for the current view
    private boolean mResetLayout;
    private final SparseArray<View>
            mChildViews = new SparseArray<View>(3);
    // Shadows the children of the adapter view
    // but with more sensible indexing
    private final LinkedList<View>
            mViewCache = new LinkedList<View>();
    private boolean mUserInteracting;  // Whether the user is interacting
    private boolean mScaling;    // Whether the user is currently pinch zooming
    private float mScale = 1.0f;
    private float mDefaultScale = 1.0f;// 存放默认宽度占满时的比例

    // --- 连续拼页模式新增字段 ---
    // 文档坐标系中的绝对滚动偏移 (正数 = 向下滚动)
    private int mScrollY = 0;
    // 每页缩放后的像素高度，key=页面索引
    private final SparseArray<Integer> mPageHeights = new SparseArray<>();
    // 每页在文档坐标系中的 Y 坐标(top)，key=页面索引
    private final SparseArray<Integer> mPagePositions = new SparseArray<>();
    // 文档总高度(px)
    private int mTotalDocumentHeight = 0;
    private int mPendingCurrentAfterRestore = -1;
    // 页面位置缓存失效标记 (缩放/批注后置 true)
    private boolean mPositionsDirty = true;
    // 页面原始尺寸缓存(从 PageAdapter 获取的 PointF)
    private final SparseArray<PointF> mPageSizes = new SparseArray<>();
    // 可见区域上下各保留的屏幕倍数作为缓冲区（预加载范围）
    private static final int VISUAL_BUFFER_SCREENS = 2;

    private int mXScroll;    // Scroll amounts recorded from events.
    private int mYScroll;    // and then accounted for in onLayout
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private Scroller mScroller;
    private Stepper mStepper;
    private int mScrollerLastX;
    private int mScrollerLastY;
    private float mLastScaleFocusX;
    private float mLastScaleFocusY;

    public Stack<Integer> mHistory;

    /**
     * 是否正在批注中，=true时不拦截触摸事件
     */
    private boolean isInAnnotation;
    /**
     * 批注画板正在双指滑动中，阻止 settle
     */
    private boolean mAnnotationMultiTouch;
    /**
     * <li>签名期间禁止缩放页面</li>
     * <li>签名期间禁止通过惯性拖动进行翻页</li>
     * <li>签名期间禁止通过滑动翻页</li>
     */
    private boolean isSigning;
    /**
     * 模拟滑动中
     */
    private boolean isSimulating;

    private int savedLeft, savedTop, savedRight, savedBottom;

    public void setSigning(boolean isSigning) {
        this.isSigning = isSigning;
    }

    public void savePosition() {
        View cv = mChildViews.get(mCurrent);
        if (cv == null) return;
        savedLeft = cv.getLeft();
        savedTop = cv.getTop();
        savedRight = cv.getRight();
        savedBottom = cv.getBottom();
        Debugger.d("模拟手指拖动 保存：mCurrent=" + mCurrent + ",方位：" + savedLeft + "," + savedTop + "," + savedRight + "," + savedBottom);
    }

    public void restorePosition() {
        View cv = mChildViews.get(mCurrent);
        if (cv == null) return;
        Debugger.d("模拟手指拖动 恢复：mCurrent=" + mCurrent + ",方位：" + cv.getLeft() + "," + cv.getTop() + "," + cv.getRight() + "," + cv.getBottom());
        if (savedTop != 0) {
            simulateSwipeAsync(this, 0, 0, 0, savedTop, 200);
        }
    }

    /**
     * 通过模拟手指滑动实现恢复批注前的位置
     */
    private void simulateSwipeAsync(View view, float startX, float startY, float endX, float endY, long duration) {
        isSimulating = true;
        Handler handler = new Handler(Looper.getMainLooper());
        long downTime = SystemClock.uptimeMillis();

        // 发送 DOWN
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0);
        view.dispatchTouchEvent(downEvent);
        downEvent.recycle();
        Debugger.d("模拟滑动 按下");

        int steps = (int) (duration / 16);
        float stepX = (endX - startX) / steps;
        float stepY = (endY - startY) / steps;

        for (int i = 1; i <= steps; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                long eventTime = SystemClock.uptimeMillis();
                float currentX = startX + stepX * index;
                float currentY = startY + stepY * index;
                MotionEvent moveEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, currentX, currentY, 0);
                view.dispatchTouchEvent(moveEvent);
                moveEvent.recycle();
            }, i * 16);
        }

        handler.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, endX, endY, 0);
            view.dispatchTouchEvent(upEvent);
            upEvent.recycle();
            Debugger.d("模拟滑动 抬起");
            isSimulating = false;
        }, duration);
    }

    public static abstract class ViewMapper {
        public abstract void applyToView(View view);
    }

    public interface ScaleBoundsProvider {
        float getMinScale();

        float getMaxScale();
    }

    private ScaleBoundsProvider scaleBoundsProvider;

    public ReaderView(Context context) {
        super(context);
        setup(context);
    }

    public ReaderView(Context context, float scale) {
        super(context);
        mScale = scale;
        mDefaultScale = scale;
        setup(context);
    }

    public ReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }

    public ReaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setup(context);
    }

    public void setScale(float scale) {
        mScale = clampScale(scale);
        mDefaultScale = mScale;
    }

    public float getScale() {
        return mScale;
    }

    public void setScaleBoundsProvider(ScaleBoundsProvider provider) {
        scaleBoundsProvider = provider;
    }

    private float clampScale(float scale) {
        float min = MIN_SCALE;
        float max = MAX_SCALE;
        if (scaleBoundsProvider != null) {
            min = scaleBoundsProvider.getMinScale();
            max = scaleBoundsProvider.getMaxScale();
        }
        min = Math.max(0.01f, min);
        max = Math.max(min, max);
        return Math.min(Math.max(scale, min), max);
    }

    /** 获取文档绝对滚动偏移（供外部标注确定操作页用） */
    public int getDocumentScrollY() {
        return mScrollY;
    }

    /** 恢复文档绝对滚动偏移，避免重建页面后跳到其它页或页顶 */
    public void setDocumentScrollY(int scrollY) {
        recalculatePagePositions();
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        mScrollY = Math.max(0, Math.min(maxScroll, scrollY));
        updateCurrent(findMostVisiblePage());
        requestLayout();
    }

    /** 按页锚点恢复位置，保持指定页在屏幕上的相对位置不变 */
    public void restorePagePosition(int pageIndex, int pageScreenTop) {
        recalculatePagePositions();
        int count = mAdapter != null ? mAdapter.getCount() : 0;
        if (count <= 0) return;
        int safePage = Math.max(0, Math.min(count - 1, pageIndex));
        int targetScrollY = mPagePositions.get(safePage, 0) - pageScreenTop;
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        mScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));
        mPendingCurrentAfterRestore = safePage;
        updateCurrent(safePage);
        requestLayout();
    }

    private void updateCurrent(int newCurrent) {
        if (mAdapter != null && newCurrent != mCurrent && newCurrent >= 0 && newCurrent < mAdapter.getCount()) {
            onMoveOffChild(mCurrent);
            mCurrent = newCurrent;
            onMoveToChild(mCurrent);
        }
    }

    /** 根据屏幕 Y 坐标查找所在的页面索引 */
    public int findPageAtScreenY(float screenY) {
        recalculatePagePositions();
        return findPageAtY(mScrollY + (int) screenY);
    }

    /** 获取指定页在屏幕上的 Y 坐标 */
    public int getPageScreenTop(int pageIndex) {
        recalculatePagePositions();
        return mPagePositions.get(pageIndex, 0) - mScrollY;
    }

    /** 获取指定页在文档坐标系中的 Y 坐标 */
    public int getPageDocTop(int pageIndex) {
        recalculatePagePositions();
        return mPagePositions.get(pageIndex, 0);
    }

    /** 获取指定页缩放后像素高度 */
    public int getPageDisplayHeight(int pageIndex) {
        recalculatePagePositions();
        return getPageHeight(pageIndex);
    }

    /** 获取指定页缩放后像素宽度 */
    public int getPageDisplayWidth(int pageIndex) {
        View child = mChildViews.get(pageIndex);
        if (child != null && child.getMeasuredWidth() > 0) {
            return child.getMeasuredWidth();
        }
        return Math.max(1, (int) (getWidth() * mScale));
    }

    /** 获取指定页在屏幕上的 X 坐标 */
    public int getPageScreenLeft(int pageIndex) {
        View child = mChildViews.get(pageIndex);
        if (child != null) {
            return child.getLeft();
        }
        return (getWidth() - getPageDisplayWidth(pageIndex)) / 2;
    }

    private void setup(Context context) {
        mContext = context;
        mGestureDetector = new GestureDetector(context, this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mScroller = new Scroller(context);
        mStepper = new Stepper(this, this);
        mHistory = new Stack<Integer>();

        // Get the screen size etc to customise tap margins.
        // We calculate the size of 1 inch of the screen for tapping.
        // On some devices the dpi values returned are wrong, so we
        // sanity check it: we first restrict it so that we are never
        // less than 100 pixels (the smallest Android device screen
        // dimension I've seen is 480 pixels or so). Then we check
        // to ensure we are never more than 1/5 of the screen width.
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        tapPageMargin = (int) dm.xdpi;
        if (tapPageMargin < 100)
            tapPageMargin = 100;
        if (tapPageMargin > dm.widthPixels / 5)
            tapPageMargin = dm.widthPixels / 5;
    }

    public boolean popHistory() {
        if (mHistory.empty())
            return false;
        setDisplayedViewIndex(mHistory.pop());
        return true;
    }

    public void pushHistory() {
        mHistory.push(mCurrent);
    }

    public int getDisplayedViewIndex() {
        return mCurrent;
    }

    public void setDisplayedViewIndex(int i) {
        if (mAdapter != null && 0 <= i && i < mAdapter.getCount()) {
            onMoveOffChild(mCurrent);
            mCurrent = i;
            onMoveToChild(i);
            // 转为连续滚动：定位到目标页顶部
            recalculatePagePositions();
            mScrollY = mPagePositions.get(i, 0);
            mPendingCurrentAfterRestore = i;
            mResetLayout = true;
            requestLayout();
        }
    }

    public void moveToNext() {
        // 连续拼页：向下滚动一屏
        smartMoveForwards();
    }

    public void moveToPrevious() {
        // 连续拼页：向上滚动一屏
        smartMoveBackwards();
    }

    // When advancing down the page, we want to advance by about
    // 90% of a screenful. But we'd be happy to advance by between
    // 80% and 95% if it means we hit the bottom in a whole number
    // of steps.
    private int smartAdvanceAmount(int screenHeight, int max) {
        int advance = (int) (screenHeight * 0.9 + 0.5);
        int leftOver = max % advance;
        int steps = max / advance;
        if (leftOver == 0) {
            // We'll make it exactly. No adjustment
        } else if ((float) leftOver / steps <= screenHeight * 0.05) {
            // We can adjust up by less than 5% to make it exact.
            advance += (int) ((float) leftOver / steps + 0.5);
        } else {
            int overshoot = advance - leftOver;
            if ((float) overshoot / steps <= screenHeight * 0.1) {
                // We can adjust down by less than 10% to make it exact.
                advance -= (int) ((float) overshoot / steps + 0.5);
            }
        }
        if (advance > max)
            advance = max;
        return advance;
    }

    public void smartMoveForwards() {
        // 连续拼页：前进约 90% 的屏幕高度（向下滚动 = mScrollY 增大，scroller 需负方向）
        recalculatePagePositions();
        int screenHeight = getHeight();
        int advance = smartAdvanceAmount(screenHeight, Math.max(0, mTotalDocumentHeight - mScrollY));
        mScrollerLastX = mScrollerLastY = 0;
        mScroller.startScroll(0, 0, 0, -advance, 400);
        mStepper.prod();
    }

    public void smartMoveBackwards() {
        // 连续拼页：后退约 90% 的屏幕高度（向上滚动 = mScrollY 减小，scroller 需正方向）
        int advance = smartAdvanceAmount(getHeight(), mScrollY);
        mScrollerLastX = mScrollerLastY = 0;
        mScroller.startScroll(0, 0, 0, advance, 400);
        mStepper.prod();
    }

    public void resetupChildren() {
        for (int i = 0; i < mChildViews.size(); i++)
            onChildSetup(mChildViews.keyAt(i), mChildViews.valueAt(i));
    }

    public void applyToChildren(ViewMapper mapper) {
        for (int i = 0; i < mChildViews.size(); i++)
            mapper.applyToView(mChildViews.valueAt(i));
    }

    public void releaseAllBitmaps() {
        for (int i = 0; i < mChildViews.size(); i++) {
            View v = mChildViews.valueAt(i);
            if (v instanceof PageView) {
                ((PageView) v).releaseBitmaps();
            }
        }
        for (View v : mViewCache) {
            if (v instanceof PageView) {
                ((PageView) v).releaseBitmaps();
            }
        }
        mChildViews.clear();
        mViewCache.clear();
    }

    /**
     * 批注后调用
     */
    public void afterAnnotation() {
        Debugger.i("afterAnnotation：start");

        mResetLayout = true;
        if (mAdapter == null) {
            Debugger.i("afterAnnotation：adapter is null");
            return;
        }

        //由于页面和屏幕的大小都发生了变化，导致大小和位图无效，因此所有页面视图都需要重新创建。
        mAdapter.refresh();
        int numChildren = mChildViews.size();
        for (int i = 0; i < numChildren; i++) {
            View v = mChildViews.valueAt(i);
            onNotInUse(v);
            removeViewInLayout(v);
        }
        mChildViews.clear();
        //需要清理缓存，不然会在加载存在批注的页面时闪烁
        mViewCache.clear();

        // 连续拼页：页面高度可能变化
        mPageHeights.clear();
        mPositionsDirty = true;

        requestLayout();
        invalidate();

        Debugger.i("afterAnnotation：end");
    }

    public void refresh() {
        Debugger.i("ReaderView refresh");
        mResetLayout = true;

        mScale = mDefaultScale;
        mXScroll = mYScroll = 0;
        mScrollY = 0;

        //由于页面和屏幕的大小都发生了变化，导致大小和位图无效，因此所有页面视图都需要重新创建。
        mAdapter.refresh();
        for (int i = 0; i < mChildViews.size(); i++) {
            View v = mChildViews.valueAt(i);
            onNotInUse(v);
            removeViewInLayout(v);
        }
        mChildViews.clear();
        mViewCache.clear();

        // 连续拼页：位置需要完全重算
        mPageHeights.clear();
        mPageSizes.clear();
        mPositionsDirty = true;

        requestLayout();
    }

    public View getView(int i) {
        return mChildViews.get(i);
    }

    public View getDisplayedView() {
        return mChildViews.get(mCurrent);
    }

    public void run() {
        Debugger.e(TAG, "run: mScroller.isFinished() =" + mScroller.isFinished() + ",mUserInteracting=" + mUserInteracting);
        if (!mScroller.isFinished()) {
            mScroller.computeScrollOffset();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            mXScroll += x - mScrollerLastX;
            mYScroll += y - mScrollerLastY;
            mScrollerLastX = x;
            mScrollerLastY = y;
            requestLayout();
            mStepper.prod();
        }
        // 连续拼页：惯性结束后由 onLayout2 统一处理 settle
    }

    public boolean onDown(MotionEvent arg0) {
        Debugger.i(TAG, "GestureDetector onDown ");
        mScroller.forceFinished(true);
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        Debugger.i(TAG, "GestureDetector onFling velocityX:" + velocityX + ",velocityY:" + velocityY + ",mScaling:" + mScaling + ",isSimulating:" + isSimulating);
        if (mScaling) return true;
        if (isSimulating) return true;

        // 连续拼页：Fling 直接以全局坐标滚动
        recalculatePagePositions();
        Rect bounds = getGlobalScrollBounds();
        mScrollerLastX = mScrollerLastY = 0;
        mScroller.fling(0, 0, (int)velocityX, (int)velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
        mStepper.prod();
        return true;
    }

    public void onLongPress(MotionEvent e) {
        Debugger.i(TAG, "GestureDetector onLongPress ");
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        Debugger.i(TAG, "GestureDetector onScroll distanceX:" + distanceX + ",distanceY:" + distanceY);
        PageView pageView = (PageView) getDisplayedView();
        if (!tapDisabled)
            onDocMotion();
        if (!mScaling) {
            mXScroll -= distanceX;
            mYScroll -= distanceY;
            requestLayout();
        }
        return true;
    }

    public void onShowPress(MotionEvent e) {
        Debugger.i(TAG, "GestureDetector onShowPress ");
    }

    public void defaultScale(float scale) {
        Debugger.i(TAG, "ReaderView.defaultScale: " + scale);
        float previousScale = mScale;
        mScale = clampScale(scale);

        // 连续拼页：缩放后页面高度变化，需重算位置
        mPageHeights.clear();
        mPositionsDirty = true;
        float factor = mScale / previousScale;
        int centerY = mScrollY + getHeight() / 2;
        mScrollY = (int)(centerY * factor - getHeight() / 2);
        requestLayout();
    }

    public boolean onScale(ScaleGestureDetector detector) {
        /* *** 签名期间禁止缩放页面 *** */
        if (isSigning) return false;
        float previousScale = mScale;
        float scaleFactor = detector.getScaleFactor();
        mScale = clampScale(mScale * scaleFactor);

        // 连续拼页：缩放后页面高度变化，需重算位置
        mPageHeights.clear();
        mPositionsDirty = true;
        float factor = mScale / previousScale;
        float focusY = detector.getFocusY();
        int docFocusY = mScrollY + (int)focusY;
        mScrollY = (int)(docFocusY * factor - focusY);
        Debugger.i(TAG, "ReaderView.onScale: previousScale:" + previousScale
                + "\nscaleFactor:" + scaleFactor
                + "\nmScale:" + mScale
                + "\nfactor:" + factor
                + "\nmScrollY:" + mScrollY);
        requestLayout();
        return true;
    }

    public boolean onScaleBegin(ScaleGestureDetector detector) {
        tapDisabled = true;
        mScaling = true;
        // Ignore any scroll amounts yet to be accounted for: the
        // screen is not showing the effect of them, so they can
        // only confuse the user
        mXScroll = mYScroll = 0;
        mLastScaleFocusX = mLastScaleFocusY = -1;
        return true;
    }

    public void onScaleEnd(ScaleGestureDetector detector) {
        mScaling = false;
//        Debugger.i(TAG, "ReaderView.onScaleEnd: "
//                + "\nmScale:" + mScale
//                + "\nfactor:" + detector.getScaleFactor()
//                + "\nmXScroll:" + mXScroll
//                + "\nmYScroll:" + mYScroll
//                + "\nmLastScaleFocusX:" + mLastScaleFocusX
//                + "\nmLastScaleFocusY:" + mLastScaleFocusY
//        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            tapDisabled = false;
        }

        mScaleGestureDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            mUserInteracting = true;
        }
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            mUserInteracting = false;
            // 连续拼页：抬指后触发一次 layout，由 onLayout2 统一处理 mCurrent 更新和 settle
            if (mScroller.isFinished()) requestLayout();
        }
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_CANCEL) {
            mUserInteracting = false;
            mScaling = false;
            if (mScroller.isFinished()) requestLayout();
        }

        requestLayout();
        return !isInAnnotation;
    }

    public void setAnnotation(boolean isInAnnotation) {
        this.isInAnnotation = isInAnnotation;
    }

    /** 告知 ReaderView 批注画板正在双指手势中，期间阻止 settle */
    public void setAnnotationMultiTouch(boolean multiTouching) {
        this.mAnnotationMultiTouch = multiTouching;
    }

    /**
     * 批注画板双指平移 PDF 上下文（由 AnnotationArtBoard 调用）。
     * dx,dy: 手指双指中心在屏幕上的位移量（像素）
     */
    public int scrollBy(float dx, float dy) {
        recalculatePagePositions();
        mXScroll = 0;
        mYScroll = 0;
        mScrollY += (int) dy;
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        if (mScrollY < 0) mScrollY = 0;
        if (mScrollY > maxScroll) mScrollY = maxScroll;
        requestLayout();
        return mScrollY;
    }

    /**
     * 批注画板双指缩放 PDF 上下文（由 AnnotationArtBoard 调用）。
     */
    public void zoomBy(float scaleFactor, float focusX, float focusY) {
        if (isSigning) return;
        float prev = mScale;
        mScale = clampScale(mScale * scaleFactor);
        float factor = mScale / prev;
        int docFocusY = mScrollY + (int) focusY;
        mScrollY = (int) (docFocusY * factor - focusY);
        mPageHeights.clear();
        mPositionsDirty = true;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int n = getChildCount();
        for (int i = 0; i < n; i++)
            measureView(getChildAt(i));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        try {
            onLayout2(changed, left, top, right, bottom);
        } catch (Exception e) {//java.lang.OutOfMemoryError
            Debugger.e(TAG, e);
        }
    }

    private void onLayout2(boolean changed, int left, int top, int right,
                           int bottom) {
        Debugger.i(TAG, "onLayout2: start changed:" + changed + "," + left + "," + top + "," + right + "," + bottom);
        if (isInEditMode()) return;

        int screenWidth = right - left;
        int screenHeight = bottom - top;
        int count = mAdapter != null ? mAdapter.getCount() : 0;
        if (screenWidth <= 0 || screenHeight <= 0 || count <= 0) {
            Debugger.i(TAG, "onLayout2: skip invalid size/count width=" + screenWidth + ",height=" + screenHeight + ",count=" + count);
            return;
        }

        // --- 1. 确保位置计算最新 ---
        recalculatePagePositions();

        // --- 2. 处理滚动 ---
        if (mResetLayout) {
            mResetLayout = false;
            // 重置场景：清空所有子View
            int numChildren = mChildViews.size();
            for (int i = 0; i < numChildren; i++) {
                View v = mChildViews.valueAt(i);
                onNotInUse(v);
                mViewCache.add(v);
                removeViewInLayout(v);
            }
            mChildViews.clear();
            mXScroll = mYScroll = 0;
        } else {
            // 增量滚动：将 mXScroll/mYScroll 累积到 mScrollY
            // 注意：mYScroll 负值表示内容向下滚动(页面向上移动)，对应 mScrollY 增大
            if (mXScroll != 0 || mYScroll != 0) {
                mScrollY -= mYScroll;
                mXScroll = mYScroll = 0;
            }
            // 签名期间禁止翻页，不更新mScrollY
        }

        // --- 3. 约束滚动范围 ---
        int maxScroll = Math.max(0, mTotalDocumentHeight - screenHeight);
        if (mScrollY < 0) mScrollY = 0;
        if (mScrollY > maxScroll) mScrollY = maxScroll;

        Debugger.d(TAG, "onLayout2: mScrollY=" + mScrollY + ", mCurrent=" + mCurrent + ", totalHeight=" + mTotalDocumentHeight);

        // --- 4. 确定可见页面范围 ---
        int viewTop = mScrollY;
        int viewBottom = mScrollY + screenHeight;
        int firstVisible = findPageAtY(viewTop);
        int lastVisible = findPageAtY(viewBottom);

        // --- 5. 回收缓冲区外的页面 ---
        recycleOutside(firstVisible, lastVisible);

        // --- 6. 加载、重算、布局可见范围内的页面 ---
        // 分三步：(a) 加载子View并测量 → 更新 mPageHeights
        //        (b) 若高度变化，重算 mPagePositions 并重锚 mScrollY
        //        (c) 用最终的 mPagePositions/mScrollY 统一布局
        int buffer = VISUAL_BUFFER_SCREENS;
        int loadFirst = Math.max(0, firstVisible - buffer);
        int loadLast = Math.min(count - 1, lastVisible + buffer);

        // (a) 加载所有子View（触发 measureView，更新 mPageHeights）
        for (int i = loadFirst; i <= loadLast; i++) {
            getOrCreateChild(i);
        }

        // (b) 若子View加载改动了 mPageHeights（mPositionsDirty=true），
        //     重算全局页面位置并重锚 mScrollY，消除估算高度与实测高度的不一致。
        if (mPositionsDirty) {
            if (mPendingCurrentAfterRestore >= 0) {
                int pendingPage = Math.max(0, Math.min(count - 1, mPendingCurrentAfterRestore));
                recalculatePagePositions();
                int constrainedMaxScroll = Math.max(0, mTotalDocumentHeight - screenHeight);
                mScrollY = Math.max(0, Math.min(constrainedMaxScroll, mPagePositions.get(pendingPage, 0)));
            } else {
                int centerDocY = mScrollY + screenHeight / 2;
                int prevCenterPage = 0;
                for (int p = 0; p < count; p++) {
                    int pageTop = mPagePositions.get(p, 0);
                    int h = getPageHeight(p);
                    if (centerDocY >= pageTop && centerDocY < pageTop + h) {
                        prevCenterPage = p;
                        break;
                    }
                    if (p == count - 1) prevCenterPage = p;
                }
                int prevScreenTop = mPagePositions.get(prevCenterPage, 0) - mScrollY;

                recalculatePagePositions();

                if (prevCenterPage >= 0 && prevCenterPage < count) {
                    int targetScrollY = mPagePositions.get(prevCenterPage, 0) - prevScreenTop;
                    int constrainedMaxScroll = Math.max(0, mTotalDocumentHeight - screenHeight);
                    int newScrollY = Math.max(0, Math.min(constrainedMaxScroll, targetScrollY));
                    if (newScrollY != mScrollY) {
                        mScrollY = newScrollY;
                    }
                }
            }
        }

        // (c) 统一布局：使用最终一致的位置信息
        for (int i = loadFirst; i <= loadLast; i++) {
            View child = mChildViews.get(i);
            if (child == null) continue;
            int pageTop = mPagePositions.get(i, 0);
            int childW = child.getMeasuredWidth();
            int childH = child.getMeasuredHeight();
            int cx = (screenWidth - childW) / 2;
            int cy = pageTop - mScrollY;
            child.layout(cx, cy, cx + childW, cy + childH);
        }

        // --- 7. 滚动停止时更新 mCurrent ---
        boolean settled = !mUserInteracting && mScroller.isFinished() && !mAnnotationMultiTouch;
        if (settled) {
            if (mPendingCurrentAfterRestore >= 0) {
                updateCurrent(mPendingCurrentAfterRestore);
                mPendingCurrentAfterRestore = -1;
            } else {
                updateCurrent(findMostVisiblePage());
            }
        }

        Debugger.i(TAG, "onLayout2: invalidate mCurrent=" + mCurrent + " mScrollY=" + mScrollY);
        invalidate();
        Debugger.i(TAG, "onLayout2: end");
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null && mAdapter != adapter)
            mAdapter.releaseBitmaps();
        mAdapter = (PageAdapter) adapter;

        requestLayout();
    }

    @Override
    public void setSelection(int arg0) {
        throw new UnsupportedOperationException(getContext().getString(R.string.not_supported));
    }

    private View getCached() {
        if (mViewCache.size() == 0)
            return null;
        else
            return mViewCache.removeFirst();
    }

    private View getOrCreateChild(int i) {
        View v = mChildViews.get(i);
        if (v == null) {
            v = mAdapter.getView(i, getCached(), this);
            addAndMeasureChild(i, v);
            onChildSetup(i, v);
        }

        return v;
    }

    private void addAndMeasureChild(int i, View v) {
        LayoutParams params = v.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        addViewInLayout(v, 0, params, true);
        mChildViews.append(i, v); // Record the view against its adapter index
        measureView(v);
        // 缓存实际像素高度，标记位置需要重算
        mPageHeights.put(i, v.getMeasuredHeight());
        mPositionsDirty = true;
    }

    private void measureView(View v) {
        // 查看视图所需的尺寸
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        // 连续拼页：所有页面按统一宽度缩放，不再限制高度
        int measuredWidth = Math.max(1, v.getMeasuredWidth());
        int measuredHeight = Math.max(1, v.getMeasuredHeight());
        int parentWidth = Math.max(1, getWidth());
        float scale = (float) parentWidth / (float) measuredWidth;
        // 使用按当前比例因子缩放的拟合值
        int targetWidth = Math.max(1, (int) (measuredWidth * scale * mScale));
        int targetHeight = Math.max(1, (int) (measuredHeight * scale * mScale));
        v.measure(View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY));
    }

    // ====== 连续拼页辅助方法 ======

    /** 获取指定页在 mPageSizes 中缓存的原始尺寸(PointF)，未缓存则从 MuPDFCore 同步加载 */
    private PointF getCachedPageSize(int pageIndex) {
        PointF size = mPageSizes.get(pageIndex);
        if (size == null && mAdapter != null) {
            size = mAdapter.getPageSizeSync(pageIndex);
            if (size != null) mPageSizes.put(pageIndex, size);
        }
        return size;
    }

    /** 获取指定页在当前缩放下像素高度 */
    private int getPageHeight(int pageIndex) {
        Integer h = mPageHeights.get(pageIndex);
        if (h != null && h > 0) return h;
        PointF size = getCachedPageSize(pageIndex);
        if (size != null && size.x > 0) {
            return (int)(getWidth() * (size.y / size.x) * mScale);
        }
        // 兜底：假设页面宽高比 1:1
        return (int)(getWidth() * mScale);
    }

    /** 重新计算所有页面全局 Y 坐标 */
    private void recalculatePagePositions() {
        if (!mPositionsDirty) return;
        mPagePositions.clear();
        int count = mAdapter != null ? mAdapter.getCount() : 0;
        int y = 0;
        for (int i = 0; i < count; i++) {
            mPagePositions.put(i, y);
            int h = getPageHeight(i);
            y += h + GAP;
        }
        mTotalDocumentHeight = y > GAP ? y - GAP : 0;
        mPositionsDirty = false;
    }

    /** 查找文档 Y 坐标落在哪一页 */
    public int findPageAtY(int y) {
        recalculatePagePositions();
        int count = mAdapter != null ? mAdapter.getCount() : 0;
        for (int i = 0; i < count; i++) {
            int top = mPagePositions.get(i, 0);
            int bottom = top + getPageHeight(i);
            if (y < top) {
                if (i == 0) return 0;
                int prevBottom = mPagePositions.get(i - 1, 0) + getPageHeight(i - 1);
                return (y - prevBottom <= top - y) ? i - 1 : i;
            }
            if (y >= top && y < bottom) return i;
        }
        // y 落在文档末尾之后 → 返回最后一页
        return count > 0 ? count - 1 : 0;
    }

    /** 返回视口中心线所在的页面索引 */
    private int findMostVisiblePage() {
        recalculatePagePositions();
        int count = mAdapter != null ? mAdapter.getCount() : 0;
        if (count <= 0) return 0;
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        if (mScrollY >= maxScroll - 1) return count - 1;
        int centerY = mScrollY + getHeight() / 2;
        return findPageAtY(centerY);
    }

    /** 全局滚动边界（相对于当前 mScrollY） */
    private Rect getGlobalScrollBounds() {
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        // fling 边界: Scroller 从 0 出发 → 达到 minY 时 mScrollY 增大到 maxScroll
        //                         → 达到 maxY 时 mScrollY 减小到 0
        // mScrollY = oldScrollY - scrollerY (符号修正: mYScroll 负 → 内容下滚 → mScrollY 增)
        int minY = -(maxScroll - mScrollY);
        int maxY = mScrollY;
        return new Rect(0, minY, 0, maxY);
    }

    /** 回收视口缓冲区之外的子 View */
    private void recycleOutside(int firstVisible, int lastVisible) {
        int buffer = VISUAL_BUFFER_SCREENS;
        int keepFirst = Math.max(0, firstVisible - buffer);
        int keepLast = lastVisible + buffer;
        int num = mChildViews.size();
        int[] indices = new int[num];
        for (int i = 0; i < num; i++) indices[i] = mChildViews.keyAt(i);
        for (int idx : indices) {
            if (idx < keepFirst || idx > keepLast) {
                View v = mChildViews.get(idx);
                if (v != null) {
                    onNotInUse(v);
                    mViewCache.add(v);
                    removeViewInLayout(v);
                    mChildViews.remove(idx);
                }
            }
        }
    }

    private Rect getScrollBounds(int left, int top, int right, int bottom) {
        int xmin = getWidth() - right;
        int xmax = -left;
        int ymin = getHeight() - bottom;
        int ymax = -top;

        // In either dimension, if view smaller than screen then
        // constrain it to be central
        if (xmin > xmax) xmin = xmax = (xmin + xmax) / 2;
        if (ymin > ymax) ymin = ymax = (ymin + ymax) / 2;

        return new Rect(xmin, ymin, xmax, ymax);
    }

    private Rect getScrollBounds(View v) {
        // There can be scroll amounts not yet accounted for in
        // onLayout, so add mXScroll and mYScroll to the current
        // positions when calculating the bounds.
        return getScrollBounds(v.getLeft() + mXScroll,
                v.getTop() + mYScroll,
                v.getLeft() + v.getMeasuredWidth() + mXScroll,
                v.getTop() + v.getMeasuredHeight() + mYScroll);
    }

    private Point getCorrection(Rect bounds) {
        return new Point(Math.min(Math.max(0, bounds.left), bounds.right),
                Math.min(Math.max(0, bounds.top), bounds.bottom));
    }

    private void postSettle(final View v) {
//        try {
//            throw new Exception("调用栈");
//        } catch (Exception e) {
//            Debugger.e(TAG, e);
//        }
        // onSettle and onUnsettle are posted so that the calls won't be executed until after the system has performed layout.
        post(new Runnable() {
            public void run() {
                onSettle(v);
            }
        });
    }

    private void postUnsettle(final View v) {
        post(new Runnable() {
            public void run() {
                onUnsettle(v);
            }
        });
    }

    private void slideViewOntoScreen(View v) {
        /* *** 签名期间禁止通过滑动、惯性拖动进行翻页 *** */
        if (isSigning) return;
        // 连续拼页：将滚动约束在全局有效范围内
        int maxScroll = Math.max(0, mTotalDocumentHeight - getHeight());
        if (mScrollY < 0) mScrollY = 0;
        if (mScrollY > maxScroll) mScrollY = maxScroll;
        requestLayout();
    }

    private Point subScreenSizeOffset(View v) {
        return new Point(Math.max((getWidth() - v.getMeasuredWidth()) / 2, 0),
                Math.max((getHeight() - v.getMeasuredHeight()) / 2, 0));
    }

    private static int directionOfTravel(float vx, float vy) {
        if (Math.abs(vx) > 2 * Math.abs(vy))
            return (vx > 0) ? MOVING_RIGHT : MOVING_LEFT;
        else if (Math.abs(vy) > 2 * Math.abs(vx))
            return (vy > 0) ? MOVING_DOWN : MOVING_UP;
        else
            return MOVING_DIAGONALLY;
    }

    private static boolean withinBoundsInDirectionOfTravel(Rect bounds, float vx, float vy) {
        switch (directionOfTravel(vx, vy)) {
            case MOVING_DIAGONALLY:
                return bounds.contains(0, 0);
            case MOVING_LEFT:
                return bounds.left <= 0;
            case MOVING_RIGHT:
                return bounds.right >= 0;
            case MOVING_UP:
                return bounds.top <= 0;
            case MOVING_DOWN:
                return bounds.bottom >= 0;
            default:
                throw new NoSuchElementException();
        }
    }

    protected void onTapMainDocArea() {
    }

    protected void onDocMotion() {
    }

    public void setLinksEnabled(boolean b) {
        mLinksEnabled = b;
        resetupChildren();
        invalidate();
    }

    public boolean onSingleTapUp(MotionEvent e) {
        Link link = null;
        if (!tapDisabled) {
            PageView pageView = (PageView) getDisplayedView();
            if (mLinksEnabled && pageView != null) {
                int page = pageView.hitLink(e.getX(), e.getY());
                if (page > 0) {
                    pushHistory();
                    setDisplayedViewIndex(page);
                } else {
                    onTapMainDocArea();
                }
            } else if (e.getX() < tapPageMargin) {
                smartMoveBackwards();
            } else if (e.getX() > super.getWidth() - tapPageMargin) {
                smartMoveForwards();
            } else if (e.getY() < tapPageMargin) {
                smartMoveBackwards();
            } else if (e.getY() > super.getHeight() - tapPageMargin) {
                smartMoveForwards();
            } else {
                onTapMainDocArea();
            }
        }
        return true;
    }

    protected void onChildSetup(int i, View v) {
        if (SearchTaskResult.get() != null
                && SearchTaskResult.get().pageNumber == i)
            ((PageView) v).setSearchBoxes(SearchTaskResult.get().searchBoxes);
        else
            ((PageView) v).setSearchBoxes(null);

        ((PageView) v).setLinkHighlighting(mLinksEnabled);
    }

    protected void onMoveToChild(int i) {
        if (SearchTaskResult.get() != null
                && SearchTaskResult.get().pageNumber != i) {
            SearchTaskResult.set(null);
            resetupChildren();
        }
    }

    protected void onMoveOffChild(int i) {
    }

    protected void onSettle(View v) {
    }

    protected void onUnsettle(View v) {
    }

    protected void onNotInUse(View v) {
        ((PageView) v).releaseResources();
    }
}
