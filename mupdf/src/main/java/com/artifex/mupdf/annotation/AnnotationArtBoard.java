package com.artifex.mupdf.annotation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.util.Debugger;
import com.artifex.mupdf.viewer.MuPDFCore;
import com.artifex.mupdf.viewer.PageView;
import com.artifex.mupdf.viewer.ReaderView;
import com.xlk.mupdf.library.api.MupdfOperationId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * @author Created by xlk on 2021/10/6.
 */
public class AnnotationArtBoard extends View {
    private static final String TAG = "AnnotationArtBoard";
    /**
     * 显示区域的宽高，是不变的
     */
    private int screenWidth, screenHeight;
    /**
     * 画板的宽高，会根据图片大小改变
     */
    private int artBoardWidth, artBoardHeight;
    /**
     * =true表示是通过构造器动态创建
     */
    private boolean isCreate;
    private Paint mPaint;
    /**
     * 画笔宽度
     */
    private float paintWidth = 2.0f;
    /**
     * 画笔默认颜色
     */
    private int paintColor = Color.RED;
    private final int delLineColor = Color.argb(90, 24, 255, 255);
    private final int highlightColor = Color.argb(80, 255, 255, 0);

    //设置画图样式
    public static final int DRAW_SLINE = 1;
    public static final int DRAW_CIRCLE = 2;
    public static final int DRAW_RECT = 3;
    public static final int DRAW_LINE = 4;
    public static final int DRAW_TEXT = 5;
    public static final int DRAW_ERASER = 6;
    public static final int DRAW_DELLINE = 7;
    public static final int DRAW_UNDERLINE = 8;
    public static final int DRAW_STRIKEOUT = 9;
    public static final int DRAW_FREETEXT = 10;
    /**
     * 当前画笔默认是曲线
     */
    private int currentDrawGraphics = DRAW_SLINE;
    private List<AnnotationArtBoard.DrawPath> pathList = new ArrayList<>();
    private long currentOperationId;
    private final Set<Long> erasedOperationIds = new LinkedHashSet<>();
    private Paint mBitmapPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private DrawPath drawPath;
    private final int WRAP_WIDTH = 300;
    private final int WRAP_HEIGHT = 300;
    /**
     * 是否拖动画板
     */
    public boolean drag = false;
    private final int DIFFERENCE = 1;
    private final List<Point> points = new ArrayList<>();
    private List<AnnotationBean> annotationBeans = new ArrayList<>();
    private MuPDFCore core;
    private ReaderView docView;
    /** 当前文档滚动偏移（px），用于屏幕坐标→文档坐标的转换 */
    private int documentScrollY;
    /**
     * 位图内容烘焙时的文档滚动偏移（px）。
     * 画板固定在屏幕上，而笔画提交后同时存在于 PDF 页面（文档坐标）中；
     * onDraw 时按 (bitmapScrollY - 当前滚动) 平移位图，使浮层笔画与 PDF 批注始终重合。
     */
    private int bitmapScrollY;
    /**
     * 手指按下到抬起之间为 true。
     */
    private boolean isDrawing;
    /**
     * =true 表示有一笔已提交到 PDF、正在等页面重绘渲染出来，
     * 期间浮层只显示这一笔的实时预览，页面重绘完成后撤掉，避免同一笔出现两份。
     */
    private boolean awaitAnnotationRender;

    // 双指滑动/缩放 PDF 上下文状态
    private boolean isMultiTouching;
    private float multiTouchLastCX, multiTouchLastCY;
    private static final float MULTI_SCROLL_THRESHOLD = 5f;  // px

    public AnnotationArtBoard(Context context) {
        this(context, null);
    }

    public AnnotationArtBoard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnnotationArtBoard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 删除旧批注
     */
    public AnnotationArtBoard(Context context, MuPDFCore core, ReaderView docView, int width, int height) {
        this(context);
        this.core = core;
        this.docView = docView;
        isCreate = true;
        screenWidth = width;
        screenHeight = height;
        artBoardWidth = width;
        artBoardHeight = height;
        initial();
    }

    private void initial() {
        initPaint();
        initCanvas();
    }

    public void setDrag(boolean drag) {
        this.drag = drag;
    }

    public void setDrawType(int type) {
        this.currentDrawGraphics = type;
        initPaint();
    }

    public int getDrawType() {
        return currentDrawGraphics;
    }

    public void setPaintWidth(float width) {
        paintWidth = width;
        initPaint();
    }

    public float getPaintWidth() {
        return paintWidth;
    }

    public void setPaintColor(int color) {
        paintColor = color;
        initPaint();
    }

    public int getPaintColor() {
        return paintColor;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!isCreate) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(WRAP_WIDTH, WRAP_HEIGHT);
            } else if (widthMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(WRAP_WIDTH, height);
            } else if (heightMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(width, WRAP_HEIGHT);
            }
            artBoardWidth = width;
            artBoardHeight = height;
            initial();
        }
    }

    public void initCanvas() {
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        mBitmap = Bitmap.createBitmap(artBoardWidth, artBoardHeight, Bitmap.Config.ARGB_8888);
        mBitmap.eraseColor(Color.TRANSPARENT);
        mCanvas = new Canvas(mBitmap);
        mCanvas.drawColor(Color.TRANSPARENT);//设置画布的颜色
        // 新位图为空，基准滚动位置与当前保持一致
        bitmapScrollY = currentDocumentScrollY();
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);// 抗锯齿
        mPaint.setDither(true);// 防抖动
        mPaint.setStrokeJoin(Paint.Join.ROUND);// 设置线段连接处的样式为圆弧连接
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 设置两端的线帽为圆的
        mPaint.setStrokeWidth(paintWidth);// 画笔宽度
        switch (currentDrawGraphics) {
            case DRAW_RECT:
                mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mPaint.setStrokeWidth(3f);
                mPaint.setColor(highlightColor);
                break;
            case DRAW_DELLINE:
                mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mPaint.setStrokeWidth(3f);
                mPaint.setColor(delLineColor);
                break;
            case DRAW_UNDERLINE:
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2f);
                mPaint.setColor(Color.argb(120, 0, 120, 255));
                mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 4}, 0));
                break;
            case DRAW_STRIKEOUT:
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2f);
                mPaint.setColor(Color.argb(120, 255, 60, 60));
                mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 4}, 0));
                break;
            case DRAW_FREETEXT:
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(Color.argb(120, 60, 180, 60));
                mPaint.setStrokeWidth(1f);
                break;
            case DRAW_SLINE:
            case DRAW_CIRCLE:
            case DRAW_LINE:
            case DRAW_TEXT:
                mPaint.setColor(paintColor);// 颜色
                // 预览线宽按“PDF 批注在屏幕上的实际宽度”换算，交接时粗细不跳变
                mPaint.setStrokeWidth(resolvePreviewWidthPx());
                break;
            case DRAW_ERASER:
                mPaint.setColor(Color.WHITE);
                break;
        }
    }

    /**
     * PDF 批注的线宽（pt）。
     * 与 {@code MuPdfDocumentActivity.addStrokeAnnotation} 中的 {@code paintSize = paintWidth / 3f} 保持一致。
     */
    private float annotationStrokeWidthPt() {
        return paintWidth / 3.0f;
    }

    /**
     * 把 PDF 线宽换算成当前页在屏幕上的像素宽度。
     * 预览用 {@link #paintWidth} 像素、而写入 PDF 的是 {@link #annotationStrokeWidthPt()} 磅，
     * 两者相差一个页面缩放比例；不换算的话“实时预览 → 页面批注”交接瞬间笔画会突然变粗（看着像闪屏）。
     */
    private float resolvePreviewWidthPx() {
        if (docView == null) return paintWidth;
        try {
            int docY = isDrawing ? (int) startDocY : documentScrollY + screenHeight / 2;
            float scale = docView.getPageDisplayScale(docView.findPageAtY(docY));
            if (scale > 0f) return annotationStrokeWidthPt() * scale;
        } catch (Exception e) {
            Debugger.e(TAG, e);
        }
        return paintWidth;
    }

    public Bitmap getCanvasBmp() {
        Bitmap srcBitmap = mBitmap;
        return srcBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 在之前画板上画过得显示出来
        if (mBitmap != null && !mBitmap.isRecycled()) {
            // 位图里的笔画是“烘焙那一刻的屏幕坐标”，而它已经作为 PDF 批注存在于文档坐标中。
            // 按滚动差平移，浮层笔画才会和 PDF 批注一起随文档移动（双指平移、页面重排同理）。
            int offsetY = bitmapScrollY - currentDocumentScrollY();
            if (offsetY != 0) {
                canvas.save();
                canvas.translate(0, offsetY);
                canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
                canvas.restore();
            } else {
                canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
            }
        }
        if (mPath != null) {
            canvas.drawPath(mPath, mPaint);//实时的显示
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 双指或多指：平移/缩放底层 PDF 上下文
        if (event.getPointerCount() > 1 || isMultiTouching) {
            return handleMultiTouch(event);
        }
        // 单指落笔前冻结文档：批注画板在顶层拦截触摸，ReaderView 收不到 DOWN，
        // 正在进行的惯性/翻页滚动动画会继续带着文档滑动，导致本笔坐标基准漂移。
        if (event.getAction() == MotionEvent.ACTION_DOWN && docView != null) {
            docView.stopScrollAnimation();
            documentScrollY = docView.getDocumentScrollY();
        }
        if (drag) {
            drag(event);
        } else {
            draw(event);
        }
        return true;
    }

    /** 双指手势处理：平移 PDF，标注保持页面相对位置不变 */
    private boolean handleMultiTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 丢弃进行中的单指绘制
                mPath = null;
                points.clear();
                isDrawing = false;
                awaitAnnotationRender = false;
                invalidate();
                isMultiTouching = true;
                if (docView != null) {
                    // 双指接管前先冻结动画，之后仅由本画板驱动滚动
                    docView.stopScrollAnimation();
                    docView.setAnnotationMultiTouch(true);
                    documentScrollY = docView.getDocumentScrollY();
                }
                multiTouchLastCX = get2CenterX(event);
                multiTouchLastCY = get2CenterY(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isMultiTouching) {
                    isMultiTouching = true;
                    if (docView != null) docView.setAnnotationMultiTouch(true);
                    multiTouchLastCX = get2CenterX(event);
                    multiTouchLastCY = get2CenterY(event);
                    break;
                }
                float cx = get2CenterX(event);
                float cy = get2CenterY(event);
                float dx = cx - multiTouchLastCX;
                float dy = cy - multiTouchLastCY;

                // 双指仅滑动，不支持缩放
                // dy 取反：手指上滑(cy减小→dy为负)文档前进(mScrollY增大)
                if ((Math.abs(dx) > MULTI_SCROLL_THRESHOLD || Math.abs(dy) > MULTI_SCROLL_THRESHOLD)
                        && docView != null) {
                    documentScrollY = docView.scrollBy(dx, -dy);
                    multiTouchLastCX = cx;
                    multiTouchLastCY = cy;
                    // 文档移动后需要重绘画板，否则浮层上已提交的笔画会停留在原屏幕位置
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // 不重置 isMultiTouching——某指抬起后剩余手指仍属于本次手势，
                // 若立即切回 draw() 会导致抬指边缘处误触为画笔操作
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isMultiTouching = false;
                if (docView != null) {
                    docView.setAnnotationMultiTouch(false);
                    documentScrollY = docView.getDocumentScrollY();
                }
                break;
        }
        return true;
    }

    private float get2CenterX(MotionEvent e) {
        return (e.getX(0) + e.getX(Math.min(1, e.getPointerCount() - 1))) / 2f;
    }
    private float get2CenterY(MotionEvent e) {
        return (e.getY(0) + e.getY(Math.min(1, e.getPointerCount() - 1))) / 2f;
    }

    /**
     * 拖动时按下
     */
    private float downX, downY;
    private int l = 0, t = 0, r, b;

    private void drag(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getX();
                float moveY = event.getY();
                //负数,说明是向左滑动
                float dx = moveX - downX;
                //负数,说明是向上滑动
                float dy = moveY - downY;
                int left = getLeft();
                int top = getTop();
                //左
                if (left == 0) {
                    if (dx < 0 && getRight() >= screenWidth) {
                        l = (int) (left + dx);
                    } else {
                        l = 0;
                    }
                } else if (left < 0) {
                    if (getRight() >= screenWidth) {
                        if (dx < 0) {
                            l = (int) (left + dx);
                        } else if (dx > 0) {
                            l = (int) (left + dx);
                        }
                    }
                } else {
                    l = 0;
                }
                //上
                if (top == 0) {
                    if (dy < 0 && getBottom() > screenHeight) {
                        t = (int) (top + dy);
                    } else {
                        t = 0;
                    }
                } else if (top < 0) {
                    if (dy < 0 && getBottom() > screenHeight) {
                        t = (int) (top + dy);
                    } else if (dy > 0) {
                        t = (int) (top + dy);
                    }
                } else {
                    t = 0;
                }
                //右
                r = artBoardWidth + l;
                int i1 = screenWidth - r;
                if (i1 > 0) {
                    r = screenWidth;
                    l += i1;
                }
                //下
                b = artBoardHeight + t;
                int i = screenHeight - b;
                if (i > 0) {
                    b = screenHeight;
                    t += i;
                }
                this.layout(l, t, r, b);
//                currentArtBoardWidth = r - l;
//                currentArtBoardHeight = b - t;
//                LogUtil.d(TAG, "drag -->" + "拖动后画板的宽高：" + currentArtBoardWidth + " , " + currentArtBoardHeight);
                break;
            default:
                break;
        }
    }

    private float startX, startY;
    private float startDocY;
    /**
     * 临时坐标点
     */
    private float tempX, tempY;

    private void draw(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (docView != null) documentScrollY = docView.getDocumentScrollY();
                currentOperationId = MupdfOperationId.next();
                erasedOperationIds.clear();
                isDrawing = true;
                // 新的笔画开始，上一笔的临时预览不再需要
                awaitAnnotationRender = false;
                startX = event.getX();
                startY = event.getY();
                startDocY = y + documentScrollY;
                // 即时保存模式：上次笔画已提交到 PDF，清空画板开始新笔画
                pathList.clear();
                try {
                    initCanvas();
                } catch (Exception e) {
                    // 若画板初始化失败（OOM/尺寸为0），保留上次的 mCanvas/mBitmap
                    if (mCanvas == null) {
                        Debugger.e(TAG, e);
                        break;
                    }
                }
                mPath = new Path();
                mPath.moveTo(x, y);
                tempX = x;
                tempY = y;
                initPaint();
                //只有绘制曲线的时候才去添加
                if (currentDrawGraphics == DRAW_SLINE) {
                    //添加按下时的点
                    points.add(toDocumentPoint(x, y));
                }
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                // 从双指切回单指时 mPath 可能为 null，重新初始化
                if (mPath == null) {
                    mPath = new Path();
                    mPath.moveTo(x, y);
                    tempX = x;
                    tempY = y;
                    initPaint();
                }
                switch (currentDrawGraphics) {
                    //曲线
                    case DRAW_SLINE:
                        //添加移动时的点
                        points.add(toDocumentPoint(x, y));
                        drawSLine(x, y);
                        break;
                    //圆
                    case DRAW_CIRCLE:
                        drawOval(x, y);
                        break;
                    //矩形
                    case DRAW_RECT:
                        drawRect(x, y);
                        break;
                    //下划线
                    case DRAW_DELLINE:
                        drawRect(x, y);
                        break;
                    //文字下划线-选区
                    case DRAW_UNDERLINE:
                        drawRect(x, y);
                        break;
                    //文字删除线-选区
                    case DRAW_STRIKEOUT:
                        drawRect(x, y);
                        break;
                    //自由文本-不绘制拖动轨迹
                    case DRAW_FREETEXT:
                        break;
                    //直线
                    case DRAW_LINE:
                        drawLine(x, y);
                        break;
                    //橡皮搽
                    case DRAW_ERASER:
                        eraserPath(x, y);
                        // 即时保存模式下批注已写进 PDF，擦除时必须从 PDF 层删除
                        if (core != null && docView != null) {
                            int docY = docView.getDocumentScrollY() + (int) y;
                            int pageIdx = docView.findPageAtY(docY);
                            int pageTop = docView.getPageDocTop(pageIdx);
                            int pageLeft = docView.getPageScreenLeft(pageIdx);
                            int pageW = docView.getPageDisplayWidth(pageIdx);
                            int pageH = docView.getPageDisplayHeight(pageIdx);
                            if (pageH <= 0) pageH = screenHeight;
                            float localY = y + docView.getDocumentScrollY() - pageTop;
                            long[] deletedOperationIds = core.deleteAnnotationWithOperationIds(
                                    pageIdx, pageW, pageH, x - pageLeft, localY);
                            if (deletedOperationIds.length > 0) {
                                for (long operationId : deletedOperationIds) {
                                    if (operationId > 0) erasedOperationIds.add(operationId);
                                }
                                View pv = docView.getView(pageIdx);
                                if (pv instanceof PageView) ((PageView) pv).update();
                            }
                        }
                        break;
                    default:
                        break;
                }
                tempX = x;
                tempY = y;
                invalidate();
                break;
            case MotionEvent.ACTION_UP: {
                if (docView != null) documentScrollY = docView.getDocumentScrollY();
                isDrawing = false;
                if (currentDrawGraphics == DRAW_ERASER) {
                    notifyEraseFinished();
                    points.clear();
                    mPath = null;
                    awaitAnnotationRender = false;
                    invalidate();
                    break;
                }
                long operid = currentOperationId;
                // 屏幕坐标 → 文档坐标（画板固定在屏幕，需使用采样时的滚动偏移）
                float dx = x, dy = y + documentScrollY;
                float dsx = startX, dsy = startDocY;
                if (currentDrawGraphics == DRAW_LINE) {
                    Point[] array = new Point[2];
                    array[0] = new Point(dsx, dsy);
                    array[1] = new Point(dx, dy);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_LINE, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                } else if (currentDrawGraphics == DRAW_RECT) {
                    Point[] array = new Point[2];
                    array[0] = new Point(dsx, dsy);
                    array[1] = new Point(dx, dy);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_HIGHLIGHT, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                } else if (currentDrawGraphics == DRAW_DELLINE) {
                    Point[] array = new Point[2];
                    array[0] = new Point(dsx, dy);
                    array[1] = new Point(dx, dy);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_LINE, array, 3.0f, delLineColor);
                    annotationBeans.add(annotationBean);
                    mPath = new Path();
                    mPath.moveTo(startX, y);
                    mPath.lineTo(x, y);
                } else if (currentDrawGraphics == DRAW_UNDERLINE) {
                    if (textMarkupListener != null) {
                        textMarkupListener.onTextMarkup(PDFAnnotation.TYPE_UNDERLINE,
                                new Point(dsx, dsy), new Point(dx, dy), paintColor, paintWidth);
                    }
                } else if (currentDrawGraphics == DRAW_STRIKEOUT) {
                    if (textMarkupListener != null) {
                        textMarkupListener.onTextMarkup(PDFAnnotation.TYPE_STRIKE_OUT,
                                new Point(dsx, dsy), new Point(dx, dy), paintColor, paintWidth);
                    }
                } else if (currentDrawGraphics == DRAW_FREETEXT) {
                    if (freeTextListener != null) {
                        freeTextListener.onFreeTextTap(new Point(dsx, dsy));
                    }
                } else {
                    // 墨迹：移动采样时已转换为文档坐标
                    Point[] array = list2array(points);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_INK, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                }
                if (mPath != null) {
                    boolean bakeable = currentDrawGraphics != DRAW_TEXT && currentDrawGraphics != DRAW_ERASER
                            && currentDrawGraphics != DRAW_FREETEXT
                            && currentDrawGraphics != DRAW_UNDERLINE
                            && currentDrawGraphics != DRAW_STRIKEOUT;
                    if (bakeable && strokeListener != null) {
                        // 即时保存模式：这一笔马上会写进 PDF，并在页面重绘时由 MuPDF 渲染出来。
                        // 因此不再把它烘焙进浮层位图——浮层和页面是两套独立的光栅化，
                        // 同一笔同时画两份永远无法像素级重合，看起来就是“两条线”。
                        // 这里只保留这一笔的实时预览，等页面重绘完成后再撤掉（onAnnotationRendered）。
                        awaitAnnotationRender = true;
                    } else {
                        if (bakeable) {
                            drawPath = new DrawPath();
                            drawPath.operid = operid;
                            drawPath.path = new Path(mPath);
                            drawPath.paint = new Paint(mPaint);
                            pathList.add(drawPath);
                            if (mCanvas != null) {
                                mCanvas.drawPath(mPath, mPaint);
                                // 记录烘焙时所在的文档滚动位置，供 onDraw 还原到文档空间
                                bitmapScrollY = currentDocumentScrollY();
                            }
                        }
                        mPath = null;
                    }
                    invalidate();
                }
                //不管有没有同屏都要删除
                points.clear();
                drawPath = null;
                // 即时保存：笔画完成后立即通知外部保存到 PDF
                if (strokeListener != null && !annotationBeans.isEmpty()) {
                    AnnotationBean last = annotationBeans.get(annotationBeans.size() - 1);
                    strokeListener.onStroke(last);
                }
                break;
            } // end ACTION_UP block
            case MotionEvent.ACTION_CANCEL:
                isDrawing = false;
                if (currentDrawGraphics == DRAW_ERASER) {
                    notifyEraseFinished();
                }
                points.clear();
                mPath = null;
                awaitAnnotationRender = false;
                invalidate();
                break;
            default:
                break;
        }
    }

    private void notifyEraseFinished() {
        if (eraseListener != null && !erasedOperationIds.isEmpty()) {
            long[] targets = new long[erasedOperationIds.size()];
            int index = 0;
            for (Long operationId : erasedOperationIds) {
                targets[index++] = operationId;
            }
            eraseListener.onErase(currentOperationId, targets);
        }
        erasedOperationIds.clear();
    }

    private Point toDocumentPoint(float x, float y) {
        if (docView != null) {
            documentScrollY = docView.getDocumentScrollY();
        }
        return new Point(x, y + documentScrollY);
    }

    private Point[] list2array(List<Point> points) {
        Point[] array = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            array[i] = points.get(i);
        }
        return array;
    }

    private void deleteAnnotationBean(long key) {
        for (AnnotationBean next : annotationBeans) {
            if (next.key == key) {
                next.setDeleted(true);
                return;
            }
        }
    }

    private void reAddAnnotationBean(long key) {
        for (AnnotationBean next : annotationBeans) {
            if (next.key == key) {
                next.setDeleted(false);
                return;
            }
        }
    }


    /**
     * 正常橡皮搽功能
     */
    private void eraser(float x, float y) {
        initPaint();
        drawSLine(x, y);
    }

    private void drawLine(float x, float y) {
        if (mPath == null) return;
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            mPath.moveTo(startX, startY);
            mPath.lineTo(x, y);
        }
    }

    private void drawRect(float x, float y) {
        if (mPath == null) return;
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        float sx = startX;//关键代码,
        float sy = startY;//每次进入都保存好起点坐标
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            if (sx > x) {
                float a = sx;
                sx = x;
                x = a;
            }
            if (sy > y) {
                float b = sy;
                sy = y;
                y = b;
            }
            RectF rectF = new RectF(sx, sy, x, y);
            mPath.addRect(rectF, Path.Direction.CCW);
        }
    }

    private void drawOval(float x, float y) {
        if (mPath == null) return;
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mPath.addOval(startX, startY, x, y, Path.Direction.CCW);
            } else {
                float r = Math.abs(y - startY) / 2;
                float rx = x - ((x - startX) / 2);//有可能相减是负数
                float ry = y - ((y - startY) / 2);
                mPath.addCircle(rx, ry, r, Path.Direction.CCW);
            }
        }
    }

    private void drawSLine(float x, float y) {
        if (mPath == null) return;
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
        }
    }


    /**
     * 设置画板大小
     */
    public void setCanvasSize(int w, int h) {
        if (w > artBoardWidth || h > artBoardHeight) {
            if (w > artBoardWidth) {
                artBoardWidth = w;
            }
            if (h > artBoardHeight) {
                artBoardHeight = h;
            }
        } else {
            return;
        }
        initCanvas();
        drawAgain(pathList);
    }

    /**
     * 清空
     */
    public void clear() {
        initCanvas();
        invalidate();
        //已经退出共享后，点击清除将所有操作清空
        pathList.clear();
        mPath = null;
        awaitAnnotationRender = false;
    }

    /**
     * 共享中橡皮檫
     */
    private void eraserPath(float x, float y) {
        boolean isChanged = false;
        List<DrawPath> deletedPaths = new ArrayList<>();
        for (int i = 0; i < pathList.size(); i++) {
            DrawPath drawPath = pathList.get(i);
            if (drawPath.isDelete) continue;//已经设置成删除状态的进行过滤掉
            PathMeasure pm = new PathMeasure(drawPath.path, false);
            float length = pm.getLength();
            Path tempPath = new Path();
            pm.getSegment(0, length, tempPath, false);
            float[] fa = new float[2];
            float sc = 0;
            while (sc < 1) {
                sc += 0.001;
                pm.getPosTan(sc * length, fa, null);
                if (Math.abs((int) fa[0] - (int) x) <= 20 && Math.abs((int) fa[1] - (int) y) <= 20) {
                    sc = 1;//找到了退出while循环
                    isChanged = true;
                    initCanvas();
                    drawPath.setDeleted(true, i);
                    //也要进行删除待绘制集合中的数据
                    deleteAnnotationBean(drawPath.operid);
                    deletedPaths.add(drawPath);
                }
            }
        }
        if (isChanged) {
            pathList.removeAll(deletedPaths);
            pathList.addAll(deletedPaths);
            drawAgain(pathList);
        }
    }

    /**
     * 撤销,删除本机最后的一次操作
     */
    public void revoke() {
        if (!pathList.isEmpty()) {
            initCanvas();
            int index = pathList.size() - 1;
            DrawPath drawPath = pathList.get(index);
            if (drawPath.isDelete) {
                int deleteIndex = drawPath.deleteIndex;
                drawPath.setDeleted(false, -1);
                pathList.remove(index);
                pathList.add(deleteIndex, drawPath);
                reAddAnnotationBean(drawPath.operid);
            } else {
                pathList.remove(index);
                if (!annotationBeans.isEmpty()) {
                    annotationBeans.remove(annotationBeans.size() - 1);
                }
            }
            //将其他人的绘制信息也重新绘制
            drawAgain(pathList);
        }
    }

    /**
     * 存在一个问题，如果先绘制路径再绘制图片则会被图片覆盖掉
     *
     * @param pathList 存放绘制的信息
     */
    public void drawAgain(List<DrawPath> pathList) {
        for (DrawPath next : pathList) {
            if (next.isDelete) continue;
            if (next.paint != null) {
                if (next.path != null) {
                    mCanvas.drawPath(next.path, next.paint);
                } else if (next.text != null) {
                    if (next.lw < next.allTextWidth) {
                        funDraw(next.paint, next.height, next.cansee, (int) next.pointF.x, (int) next.pointF.y, next.text);
                    } else {
                        mCanvas.drawText(next.text, next.pointF.x, next.pointF.y, next.paint);
                    }
                }
            } /*else if (next.picdata != null) {
                drawAgain = true;
                byte[] bytes = next.picdata.toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                drawZoomBmp(bitmap);
            }*/
        }
        // 重绘相当于在当前屏幕位置重新烘焙，基准滚动位置同步为当前值
        bitmapScrollY = currentDocumentScrollY();
        invalidate();
    }

    public void funDraw(Paint paint, float height, int canSee, float fx, float fy, String text) {
        if (text.length() > canSee) {
            String canSeeText = text.substring(0, canSee);
            mCanvas.drawText(canSeeText, fx, fy, paint);
            String substring = text.substring(canSee);//获得剩下无法显示的文本
            funDraw(paint, height, canSee, fx, fy + height, substring);
        } else {
            mCanvas.drawText(text, fx, fy, paint);
        }
    }


    /** 设置当前文档滚动偏移，用于双指滑动时更新坐标基准 */
    public void setDocumentScrollY(int scrollY) {
        this.documentScrollY = scrollY;
    }

    /**
     * 页面已把这一笔的批注渲染出来，撤掉浮层上的临时预览。
     * 由页面重绘完成回调触发；若此时用户正在画下一笔则跳过，
     * 避免误删正在绘制的预览（下一笔 ACTION_DOWN 时也会自行清理）。
     */
    public void onAnnotationRendered() {
        if (!awaitAnnotationRender || isDrawing) return;
        awaitAnnotationRender = false;
        mPath = null;
        invalidate();
    }

    public int getDocumentScrollY() {
        return documentScrollY;
    }

    /**
     * 取当前文档滚动偏移：优先直接读 ReaderView，避免缓存值与实际滚动不同步。
     */
    private int currentDocumentScrollY() {
        if (docView != null) {
            documentScrollY = docView.getDocumentScrollY();
        }
        return documentScrollY;
    }

    public static class DrawPath {
        public boolean isDelete;//=true 表示橡皮擦操作
        public int deleteIndex;//进行删除操作的操作列表中的索引位
        public Paint paint; //画笔
        public Path path = null; //路径
        public long operid;//操作ID

        public String text = null;//绘制的文本
        public PointF pointF;//添加文本的起点（x,y）
        public int height;//文本的高度（每个字的高度）
        public int cansee;//可以显示的文本的个数
        public int lw;//可容许显示文本的区域宽度
        public int allTextWidth;//所有文本的宽度

        public void setDeleted(boolean value, int index) {
            isDelete = value;
            deleteIndex = index;
        }

        @Override
        public String toString() {
            return "DrawPath{" +
                    "isDelete=" + isDelete +
                    ", deleteIndex=" + deleteIndex +
                    ", paint=" + paint +
                    ", path=" + path +
                    ", operid=" + operid +
                    ", text='" + text + '\'' +
                    ", pointF=" + pointF +
                    ", height=" + height +
                    ", cansee=" + cansee +
                    ", lw=" + lw +
                    ", allTextWidth=" + allTextWidth +
                    '}';
        }
    }

    public interface StrokeListener {
        /** 笔画完成时立即回调（已转为文档坐标），用于即时保存到 PDF */
        void onStroke(AnnotationBean bean);
    }

    private StrokeListener strokeListener;

    public void setStrokeListener(StrokeListener l) {
        this.strokeListener = l;
    }

    public interface EraseListener {
        void onErase(long operationId, long[] targetOperationIds);
    }

    private EraseListener eraseListener;

    public void setEraseListener(EraseListener l) {
        this.eraseListener = l;
    }

    private TextMarkupListener textMarkupListener;

    public interface TextMarkupListener {
        void onTextMarkup(int type, Point start, Point end, int color, float strokeWidth);
    }

    public void setTextMarkupListener(TextMarkupListener textMarkupListener) {
        this.textMarkupListener = textMarkupListener;
    }

    private FreeTextListener freeTextListener;

    public interface FreeTextListener {
        void onFreeTextTap(Point position);
    }

    public void setFreeTextListener(FreeTextListener freeTextListener) {
        this.freeTextListener = freeTextListener;
    }

    public List<AnnotationBean> getAnnotationBeans() {
        return annotationBeans;
    }

    /** 清空画板位图和所有已画路径（即时保存模式下每次笔画提交后调用） */
    public void clearStrokes() {
        pathList.clear();
        annotationBeans.clear();
        mPath = null;
        awaitAnnotationRender = false;
        initCanvas();
        invalidate();
    }

    public void release() {
        recycleBitmapOnly();
    }

    /** 仅回收画板位图资源，不清空批注历史记录 */
    public void recycleBitmapOnly() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            Debugger.i(TAG, "recycleBitmapOnly:回收bitmap");
        }
        mBitmap = null;
        mCanvas = null;
    }
}
