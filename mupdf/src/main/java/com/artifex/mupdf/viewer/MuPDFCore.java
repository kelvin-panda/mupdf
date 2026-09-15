package com.artifex.mupdf.viewer;

import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_HIGHLIGHT;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_INK;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SCREEN;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUARE;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_STRIKE_OUT;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_UNDERLINE;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_WATERMARK;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.DisplayList;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Outline;
import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.RectI;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.fitz.android.AndroidDrawDevice;
import com.artifex.mupdf.util.Debugger;
import com.artifex.mupdf.util.Util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MuPDFCore {
    private static final String TAG = "MuPDFCore";
    private int resolution;
    private Document doc;
    private Outline[] outline;
    private int pageCount = -1;
    private int currentPage;
    private Page page;
    private float pageWidth;
    private float pageHeight;
    /**
     * PDF 批注与共享操作标识的映射，用于擦除和撤销。
     */
    private final Map<Long, List<PDFAnnotation>> annotationsByOperationId = new HashMap<>();
    private final Map<PDFAnnotation, Long> operationIdByAnnotation = new HashMap<>();
    private final Map<PDFAnnotation, Integer> pageIndexByAnnotation = new HashMap<>();

    /* Default to "A Format" pocket book size. */
    private int layoutW = 312;
    private int layoutH = 504;
    private int layoutEM = 10;

    private MuPDFCore(Document doc) {
        this.doc = doc;
        doc.layout(layoutW, layoutH, layoutEM);
        pageCount = doc.countPages();
        resolution = 160;
        currentPage = -1;
    }

    public MuPDFCore(byte buffer[], String magic) {
        this(Document.openDocument(buffer, magic));
    }

    public MuPDFCore(String filePath) {
        this(Document.openDocument(filePath));
    }

    public MuPDFCore(SeekableInputStream stm, String magic) {
        this(Document.openDocument(stm, magic));
    }

    public Document getDoc() {
        return doc;
    }

    public String getTitle() {
        return doc.getMetaData(Document.META_INFO_TITLE);
    }

    public int countPages() {
        return pageCount;
    }

    public synchronized boolean isReflowable() {
        return doc.isReflowable();
    }

    public synchronized int layout(int oldPage, int w, int h, int em) {
        if (w != layoutW || h != layoutH || em != layoutEM) {
//            Debugger.i(TAG, "layout: " + w + "," + h + ",em:" + em);
            layoutW = w;
            layoutH = h;
            layoutEM = em;
            long mark = doc.makeBookmark(doc.locationFromPageNumber(oldPage));
            doc.layout(layoutW, layoutH, layoutEM);
            currentPage = -1;
            pageCount = doc.countPages();
            outline = null;
            try {
                outline = doc.loadOutline();
            } catch (Exception ex) {
                /* ignore error */
            }
            return doc.pageNumberFromLocation(doc.findBookmark(mark));
        }
        return oldPage;
    }

    private synchronized void gotoPage(int pageNum) {
        /* TODO: page cache */
        try {
            if (pageNum > pageCount - 1)
                pageNum = pageCount - 1;
            else if (pageNum < 0)
                pageNum = 0;
            Debugger.e(TAG, "gotoPage currentPage=" + currentPage + ",pageNum =" + pageNum);
            /* *** 如果签名未完成期间激活了页面跳转，会概率性崩溃在page.destroy() *** */
            if (pageNum != currentPage) {
                if (page != null) {
                    try {
                        page.destroy();
                    } catch (Exception e) {
                        Debugger.e("page destroy 异常：" + e);
                    }
                }
                page = null;
                pageWidth = 0;
                pageHeight = 0;
                currentPage = -1;

                if (doc != null) {
                    Debugger.i(TAG, "gotoPage: loadPage pageNum:" + pageNum);
                    page = doc.loadPage(pageNum);
                    Rect b = page.getBounds();
                    pageWidth = b.x1 - b.x0;
                    pageHeight = b.y1 - b.y0;
                    Debugger.i(TAG, "gotoPage: pageNum:" + pageNum + ",pageWidth:" + pageWidth + ",pageHeight:" + pageHeight);
                } else {
                    Debugger.i(TAG, "gotoPage: doc is null");
                }

                currentPage = pageNum;
            }
        } catch (Exception e) {
            Debugger.e(e);
        }
    }

    public synchronized PointF getPageSize(int pageNum) {
        gotoPage(pageNum);
        return new PointF(pageWidth, pageHeight);
    }

    public synchronized void drawPage(Bitmap bm, int pageNum,
                                      int pageW, int pageH,
                                      int patchX, int patchY,
                                      int patchW, int patchH,
                                      Cookie cookie) {
        if (bm == null || bm.isRecycled() || pageW <= 0 || pageH <= 0 || patchW <= 0 || patchH <= 0) {
            Debugger.e(TAG, "drawPage invalid args pageNum:" + pageNum
                    + ", pageW:" + pageW + ", pageH:" + pageH
                    + ", patchW:" + patchW + ", patchH:" + patchH
                    + ", bm:" + bm);
            return;
        }
        DisplayList localDL = null;
        try {
            Debugger.d(TAG, "---drawPage start pageNum:" + pageNum);
            gotoPage(pageNum);

            if (page != null)
                try {
                    localDL = page.toDisplayList();
                } catch (Exception ex) {
                    localDL = null;
                }

            if (localDL == null || page == null)
                return;
            float zoom = resolution / 72;
            Matrix ctm = new Matrix(zoom, zoom);
            Rect bounds = page.getBounds();
            RectI bbox = new RectI(bounds.transform(ctm));
            float xscale = (float) pageW / (float) (bbox.x1 - bbox.x0);
            float yscale = (float) pageH / (float) (bbox.y1 - bbox.y0);
            ctm.scale(xscale, yscale);
            Debugger.e(TAG, "drawPage: pageNum:" + pageNum
                    + "\npageW:" + pageW + ",pageH:" + pageH
                    + "\npatchW:" + patchW + ",patchH:" + patchH
                    + "\npatchX:" + patchX + ",patchY:" + patchY
                    + "\nbounds:" + bounds
                    + "\nbbox:" + bbox
                    + "\nctm:" + ctm
                    + "\nxscale:" + xscale + ",yscale:" + yscale
                    + "\nbm:" + bm.getWidth() + "," + bm.getHeight());
            Debugger.i(TAG, "drawPage AndroidDrawDevice start pageNum:" + pageNum);
            AndroidDrawDevice dev = new AndroidDrawDevice(bm, patchX, patchY, true);
            Debugger.i(TAG, "drawPage AndroidDrawDevice end--- pageNum:" + pageNum);
            try {
                Debugger.i(TAG, "drawPage displayList.run start--- pageNum:" + pageNum);
                localDL.run(dev, ctm, cookie);
                Debugger.i(TAG, "drawPage displayList.run end--- pageNum:" + pageNum);
                dev.close();
                Debugger.i(TAG, "drawPage dev.close() end--- pageNum:" + pageNum);
            } finally {
                dev.destroy();
            }
        } catch (Throwable e) {
            String fullStackTrace = Debugger.getFullStackTrace(e);
            Debugger.e(TAG, "drawPage Throwable:" + fullStackTrace);
        } finally {
            if (localDL != null) {
                try {
                    localDL.destroy();
                } catch (Throwable e) {
                    Debugger.e(TAG, "drawPage displayList destroy failed:" + Debugger.getFullStackTrace(e));
                }
            }
        }
        Debugger.i(TAG, "drawPage end--- pageNum:" + pageNum);
    }

    public synchronized void updatePage(Bitmap bm, int pageNum,
                                        int pageW, int pageH,
                                        int patchX, int patchY,
                                        int patchW, int patchH,
                                        Cookie cookie) {
        drawPage(bm, pageNum, pageW, pageH, patchX, patchY, patchW, patchH, cookie);
    }

    public synchronized Link[] getPageLinks(int pageNum) {
        gotoPage(pageNum);
        return page != null ? page.getLinks() : null;
    }

    public synchronized int resolveLink(Link link) {
        return doc.pageNumberFromLocation(doc.resolveLink(link));
    }

    public synchronized Quad[][] searchPage(int pageNum, String text) {
        gotoPage(pageNum);
        return page.search(text);
    }

    public synchronized boolean hasOutline() {
        if (outline == null) {
            try {
                outline = doc.loadOutline();
            } catch (Exception ex) {
                /* ignore error */
            }
        }
        return outline != null;
    }

    private void flattenOutlineNodes(ArrayList<OutlineActivity.Item> result, Outline list[], String indent) {
        for (Outline node : list) {
            if (node.title != null) {
                int page = doc.pageNumberFromLocation(doc.resolveLink(node));
                result.add(new OutlineActivity.Item(indent + node.title, page));
            }
            if (node.down != null)
                flattenOutlineNodes(result, node.down, indent + "    ");
        }
    }

    public synchronized ArrayList<OutlineActivity.Item> getOutline() {
        ArrayList<OutlineActivity.Item> result = new ArrayList<OutlineActivity.Item>();
        flattenOutlineNodes(result, outline, "");
        return result;
    }

    public synchronized boolean needsPassword() {
        return doc.needsPassword();
    }

    public synchronized boolean authenticatePassword(String password) {
        return doc.authenticatePassword(password);
    }

    public float[] parseColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int alpha = Color.alpha(color);
        Debugger.i(TAG, "parseColor: color=" + color + ",[" + red + "," + green + "," + blue + "," + alpha + "]");
        // 红绿蓝 且颜色范围值都是 :0-1
        return new float[]{red / 255f, green / 255f, blue / 255f};
    }

    public void addShareInk(int pageNum, float paintSize, int paintColor, Point[] inkList) {
        List<SharedAnnotationData> data = new ArrayList<>(1);
        data.add(new SharedAnnotationData(0L, pageNum, TYPE_INK, paintSize, paintColor,
                pointArrayToFloatArray(inkList)));
        addSharedAnnotations(data);
    }

    /**
     * 批量写入远端批注。按页加载一次、更新一次，避免逐条操作反复触发文档刷新。
     */
    public synchronized void addSharedAnnotations(List<SharedAnnotationData> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        Map<Integer, List<SharedAnnotationData>> grouped = new LinkedHashMap<>();
        for (SharedAnnotationData data : dataList) {
            if (data == null || data.pageNumber <= 0 || data.points == null
                    || data.points.length < 2) continue;
            List<SharedAnnotationData> pageData = grouped.get(data.pageNumber);
            if (pageData == null) {
                pageData = new ArrayList<>();
                grouped.put(data.pageNumber, pageData);
            }
            pageData.add(data);
        }
        if (grouped.isEmpty()) return;

        try {
            for (Map.Entry<Integer, List<SharedAnnotationData>> entry : grouped.entrySet()) {
                int pageNum = entry.getKey();
                Page loadedPage = doc.loadPage(pageNum - 1);
                if (loadedPage == null) continue;
                Rect bounds = loadedPage.getBounds();
                float realWidth = bounds.x1 - bounds.x0;
                float realHeight = bounds.y1 - bounds.y0;
                PDFPage pdfPage = (PDFPage) loadedPage;
                boolean pageChanged = false;
                for (SharedAnnotationData data : entry.getValue()) {
                    Point[] pdfPoints = normalizedToPdfPoints(data.points, realWidth, realHeight);
                    PDFAnnotation annotation = createAnnotation(pdfPage, data.type,
                            data.strokeWidth, data.argb, pdfPoints);
                    if (annotation == null) continue;
                    annotation.update();
                    registerAnnotation(data.operationId, pageNum - 1, annotation);
                    pageChanged = true;
                }
                if (pageChanged) pdfPage.update();
            }
            invalidatePageCache();
        } catch (Exception e) {
            Debugger.e(TAG + " addSharedAnnotations Exception", e);
        }
    }

    /**
     *
     * @param pageNum
     * @param width
     * @param height
     * @param type    {@link PDFAnnotation#TYPE_LINE}
     * @return
     */
    public synchronized Point[] addAnnotation(int pageNum, int width, int height, int type, float paintSize, int paintColor, Point[] inkList) {
        return addAnnotation(pageNum, width, height, type, paintSize, paintColor, inkList, 0L);
    }

    public synchronized Point[] addAnnotation(int pageNum, int width, int height, int type,
                                              float paintSize, int paintColor, Point[] inkList,
                                              long operationId) {
        try {
            Point[] percentPoints = new Point[inkList.length];
            Page page = doc.loadPage(pageNum);
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            Debugger.i(TAG, "addAnnotation: pageNum:" + pageNum + ",paintSize=" + paintSize + ",page宽高=" + realWidth + "," + realHeight);
            for (int i = 0; i < inkList.length; i++) {
                Point point = inkList[i];
                float tx = point.x;
                float ty = point.y;
                point.x = realWidth / width * point.x;
                point.y = realHeight / height * point.y;
                percentPoints[i] = new Point(point.x / realWidth, point.y / realHeight);
                Debugger.i(TAG, "addAnnotation: 原坐标【" + tx + "," + ty + "】,计算后【" + point.x + "," + point.y + "】");
            }
            PDFPage pdfPage = (PDFPage) page;
            PDFAnnotation pdfAnnotation = createAnnotation(pdfPage, type, paintSize, paintColor, inkList);
            if (pdfAnnotation == null) return null;
            boolean update = pdfAnnotation.update();
            boolean update1 = pdfPage.update();
            registerAnnotation(operationId, pageNum, pdfAnnotation);
            invalidatePageCache();
            Debugger.i(TAG, "addAnnotation 添加批注 type=" + type + ",update=" + update + ",update1=" + update1);
            return percentPoints;
        } catch (Exception e) {
            Debugger.e(TAG, "addAnnotation Exception: " + e);
            e.printStackTrace();
        }
        return null;
    }

    private PDFAnnotation createAnnotation(PDFPage pdfPage, int type, float paintSize,
                                           int paintColor, Point[] points) {
        if (pdfPage == null || points == null || points.length == 0) return null;
        PDFAnnotation pdfAnnotation = pdfPage.createAnnotation(type);
        float[] color = parseColor(paintColor);
        pdfAnnotation.setColor(color);
        switch (type) {
            case TYPE_LINE: {
                if (points.length < 2) return null;
                pdfAnnotation.setBorderWidth(paintSize);
                pdfAnnotation.setLine(new Point(points[0].x, points[0].y),
                        new Point(points[1].x, points[1].y));
                break;
            }
            case TYPE_SQUARE: {
                if (points.length < 2) return null;
                pdfAnnotation.setBorderWidth(paintSize);
                pdfAnnotation.setRect(new Rect(points[0].x, points[0].y,
                        points[1].x, points[1].y));
                break;
            }
            case TYPE_HIGHLIGHT: {
                if (points.length < 2) return null;
                float x0 = points[0].x;
                float y0 = points[0].y;
                float x1 = points[1].x;
                float y1 = points[1].y;
                pdfAnnotation.setOpacity(0.5f);
                pdfAnnotation.setColor(new float[]{1f, 1f, 0f});
                Quad quad = new Quad(x0, y0, x1, y0, x0, y1, x1, y1);
                pdfAnnotation.setQuadPoints(new Quad[]{quad});
                break;
            }
            default: {
                pdfAnnotation.setBorderWidth(paintSize);
                pdfAnnotation.addInkList(points);
                break;
            }
        }
        return pdfAnnotation;
    }

    private Point[] normalizedToPdfPoints(float[] normalizedPoints, float realWidth, float realHeight) {
        Point[] points = new Point[normalizedPoints.length / 2];
        for (int i = 0; i < points.length; i++) {
            points[i] = new Point(normalizedPoints[i * 2] * realWidth,
                    normalizedPoints[i * 2 + 1] * realHeight);
        }
        return points;
    }

    private float[] pointArrayToFloatArray(Point[] points) {
        if (points == null) return new float[0];
        float[] values = new float[points.length * 2];
        for (int i = 0; i < points.length; i++) {
            values[i * 2] = points[i].x;
            values[i * 2 + 1] = points[i].y;
        }
        return values;
    }

    private void registerAnnotation(long operationId, int pageIndex, PDFAnnotation annotation) {
        if (operationId <= 0 || annotation == null) return;
        List<PDFAnnotation> annotations = annotationsByOperationId.get(operationId);
        if (annotations == null) {
            annotations = new ArrayList<>();
            annotationsByOperationId.put(operationId, annotations);
        }
        annotations.add(annotation);
        operationIdByAnnotation.put(annotation, operationId);
        pageIndexByAnnotation.put(annotation, pageIndex);
    }

    private void unregisterAnnotation(PDFAnnotation annotation) {
        if (annotation == null) return;
        Long operationId = operationIdByAnnotation.remove(annotation);
        pageIndexByAnnotation.remove(annotation);
        if (operationId == null) return;
        List<PDFAnnotation> annotations = annotationsByOperationId.get(operationId);
        if (annotations == null) return;
        annotations.remove(annotation);
        if (annotations.isEmpty()) annotationsByOperationId.remove(operationId);
    }

    /**
     * 添加文本标记标注: 下划线/删除线，自动提取选区内的文字quad。
     */
    public synchronized PDFAnnotation addTextMarkupAnnotation(int pageNum, int width, int height, int type,
                                                              Point startPt, Point endPt, float[] color, float dpiX, float dpiY) {
        try {
            if (color == null || color.length < 3) {
                Debugger.e(TAG, "addTextMarkupAnnotation: invalid color");
                return null;
            }
            Page page = doc.loadPage(pageNum);
            if (page == null) return null;
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            if (realWidth <= 0 || realHeight <= 0 || width <= 0 || height <= 0) return null;

            // widget坐标 → 页坐标
            float x0 = realWidth / width * Math.min(startPt.x, endPt.x);
            float y0 = realHeight / height * Math.min(startPt.y, endPt.y);
            float x1 = realWidth / width * Math.max(startPt.x, endPt.x);
            float y1 = realHeight / height * Math.max(startPt.y, endPt.y);
            Rect selection = new Rect(x0, y0, x1, y1);

            Debugger.i(TAG, "addTextMarkupAnnotation: page selection=" + selection + " color=" + color[0] + "," + color[1] + "," + color[2]);

            PDFPage pdfPage = (PDFPage) page;
            // dpi=72让JNI内部screen→page换算为恒等变换
            PDFAnnotation result = pdfPage.addTextMarkupAnnotation(type, selection,
                    new float[]{color[0], color[1], color[2]}, 72f, 72f);
            invalidatePageCache();
            return result;
        } catch (Exception e) {
            Debugger.e(TAG, "addTextMarkupAnnotation Exception: " + e);
        }
        return null;
    }

    /**
     * 添加自由文本标注。
     *
     * @param pos 在widget上的位置坐标
     */
    public PDFAnnotation addFreeTextAnnotation(int pageNum, int width, int height, Point pos,
                                               String text, String fontName, float fontSize, float[] color) {
        try {
            Page page = doc.loadPage(pageNum);
            if (page == null) return null;
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            Point pt = new Point(realWidth / width * pos.x, realHeight / height * pos.y);

            PDFPage pdfPage = (PDFPage) page;
            // JNI函数：自动检测CJK嵌入字体，创建FreeText标注
            PDFAnnotation annot = pdfPage.addFreeTextAnnotation(pt, text,
                    fontName != null ? fontName : "Helv", fontSize, color, text.length());
            invalidatePageCache();
            return annot;
        } catch (Exception e) {
            Debugger.e(TAG, "addFreeTextAnnotation Exception: " + e);
        }
        return null;
    }

    private void invalidatePageCache() {
        if (page != null) {
            page.destroy();
            page = null;
        }
        currentPage = -1;
        pageWidth = 0;
        pageHeight = 0;
    }

    /**
     * 水印: JNI content stream方式写入每页。
     */
    public void addContentWatermark(String text, float fontSize, float angle,
                                    float opacity, float[] color, float spacing) {
        try {
            /* doc 实际已是 PDFDocument 实例，直接 cast 避免 new PDFDocument(ptr)
             * 造成引用计数错误导致文档被意外释放 */
            PDFDocument pdfDoc = (PDFDocument) doc;
            pdfDoc.addWatermark(text, fontSize, angle, opacity, color, spacing);
            invalidatePageCache();
            Debugger.i(TAG, "addContentWatermark: done");
        } catch (Exception e) {
            Debugger.e(TAG, "addContentWatermark Exception: " + e);
        }
    }

    /**
     * 在文档末尾创建签名表格。
     */
    public void createSignatureTable(String[] names, String headerName,
                                     String headerTime, String headerImage) {
        try {
            PDFDocument pdfDoc = (PDFDocument) doc;
            pdfDoc.createSignatureTable(names, headerName, headerTime, headerImage);
            pageCount = doc.countPages();
            invalidatePageCache();
            Debugger.i(TAG, "createSignatureTable: done, pages=" + pageCount);
        } catch (Exception e) {
            Debugger.e(TAG, "createSignatureTable Exception: " + e);
        }
    }

    /**
     * 设置签名表格行的签名时间和图片。
     */
    public void setSignatureRow(int rowIndex, String time, byte[] imageRGB,
                                int imageW, int imageH, int totalNames) {
        try {
            PDFDocument pdfDoc = (PDFDocument) doc;
            pdfDoc.setSignatureRow(rowIndex, time, imageRGB, imageW, imageH, totalNames);
            invalidatePageCache();
            Debugger.i(TAG, "setSignatureRow: row=" + rowIndex + " done");
        } catch (Exception e) {
            Debugger.e(TAG, "setSignatureRow Exception: " + e);
        }
    }

    /**
     * 清除当前页面上的全部标注。
     */
    public int clearAnnotations(int pageNum) {
        try {
            Page page = doc.loadPage(pageNum);
            PDFPage pdfPage = (PDFPage) page;
            return pdfPage.clearAnnotations();
        } catch (Exception e) {
            Debugger.e(TAG, "clearAnnotations Exception: " + e);
        }
        return 0;
    }

    /**
     * 删除指定页最后一条标注（用于撤销），返回删除条数（0或1）。
     */
    public synchronized int deleteLastAnnotation(int pageNum) {
        try {
            Page page = doc.loadPage(pageNum);
            PDFPage pdfPage = (PDFPage) page;
            PDFAnnotation[] anns = pdfPage.getAnnotations();
            if (anns == null || anns.length == 0) return 0;
            PDFAnnotation annotation = anns[anns.length - 1];
            pdfPage.deleteAnnotation(annotation);
            unregisterAnnotation(annotation);
            invalidatePageCache();
            return 1;
        } catch (Exception e) {
            Debugger.e(TAG, "deleteLastAnnotation Exception: " + e);
        }
        return 0;
    }

    /**
     * 删除一组共享操作创建的批注。
     *
     * @return 实际发生变化的页索引
     */
    public synchronized List<Integer> deleteAnnotationsByOperationIds(long[] operationIds) {
        List<Integer> changedPages = new ArrayList<>();
        if (operationIds == null || operationIds.length == 0) return changedPages;
        for (long operationId : operationIds) {
            if (operationId <= 0) continue;
            List<PDFAnnotation> annotations = annotationsByOperationId.get(operationId);
            if (annotations == null || annotations.isEmpty()) continue;

            Map<Integer, List<PDFAnnotation>> grouped = new LinkedHashMap<>();
            for (PDFAnnotation annotation : new ArrayList<>(annotations)) {
                Integer pageIndex = pageIndexByAnnotation.get(annotation);
                if (pageIndex == null) continue;
                List<PDFAnnotation> pageAnnotations = grouped.get(pageIndex);
                if (pageAnnotations == null) {
                    pageAnnotations = new ArrayList<>();
                    grouped.put(pageIndex, pageAnnotations);
                }
                pageAnnotations.add(annotation);
            }
            for (Map.Entry<Integer, List<PDFAnnotation>> entry : grouped.entrySet()) {
                try {
                    Page loadedPage = doc.loadPage(entry.getKey());
                    PDFPage pdfPage = (PDFPage) loadedPage;
                    for (PDFAnnotation annotation : entry.getValue()) {
                        pdfPage.deleteAnnotation(annotation);
                        unregisterAnnotation(annotation);
                    }
                    pdfPage.update();
                    if (!changedPages.contains(entry.getKey())) {
                        changedPages.add(entry.getKey());
                    }
                } catch (Exception e) {
                    Debugger.e(TAG + " deleteAnnotationsByOperationId Exception", e);
                }
            }
            annotationsByOperationId.remove(operationId);
        }
        if (!changedPages.isEmpty()) invalidatePageCache();
        return changedPages;
    }

    public synchronized boolean hasAnnotationsForOperation(long operationId) {
        List<PDFAnnotation> annotations = annotationsByOperationId.get(operationId);
        return annotations != null && !annotations.isEmpty();
    }

    public void logAnnotations(int pageNum) {
        Page page = doc.loadPage(pageNum);
        PDFPage pdfPage = (PDFPage) page;
        PDFAnnotation[] annotations = pdfPage.getAnnotations();
        if (annotations == null) {
            return;
        }
        Debugger.i(TAG, "logAnnotations: 批注数量：" + annotations.length);
        for (PDFAnnotation annotation : annotations) {
            boolean hasInkList = annotation.hasInkList();
            boolean hasLine = annotation.hasLine();
            Debugger.i(TAG, "logAnnotations: hasInkList:" + hasInkList + ",hasLine:" + hasLine);
            if (hasInkList) {
                Debugger.i(TAG, "logAnnotations: InkListCount：" + annotation.getInkListCount());
                Point[][] inkList = annotation.getInkList();
                for (Point[] points : inkList) {
                    Debugger.d(TAG, "logAnnotations: 墨迹坐标数量：" + points.length);
                    for (Point point : points) {
                        Debugger.d(point.toString());
                    }
                }
            }
            if (hasLine) {
                Point[] line = annotation.getLine();
                Debugger.d(TAG, "logAnnotations: 直线坐标数量：" + line.length);
                for (Point point : line) {
                    Debugger.d(point.toString());
                }
            }
        }
        Debugger.i(TAG, "logAnnotations end");
    }

    static class AnnotationPathBean {
        List<Path> paths;
        PDFAnnotation pdfAnnotation;
        boolean areaHit;

        public AnnotationPathBean(List<Path> paths, PDFAnnotation pdfAnnotation) {
            this(paths, pdfAnnotation, false);
        }

        public AnnotationPathBean(List<Path> paths, PDFAnnotation pdfAnnotation, boolean areaHit) {
            this.paths = paths;
            this.pdfAnnotation = pdfAnnotation;
            this.areaHit = areaHit;
        }

        public List<Path> getPaths() {
            return paths;
        }

        public PDFAnnotation getPdfAnnotation() {
            return pdfAnnotation;
        }

        public boolean isAreaHit() {
            return areaHit;
        }
    }

    /**
     * 将一组组的点转换成 {@link Path} 对象
     *
     * @param annotations 当前页面所有的批注 {@link PDFAnnotation}
     * @return {@link AnnotationPathBean} 列表
     */
    public List<AnnotationPathBean> annotations2path(PDFAnnotation[] annotations) {
        List<AnnotationPathBean> annotationPathBeans = new ArrayList<>();
        List<Path> pathList = null;
        Path path = null;
        Debugger.i(TAG, "annotations2path: 批注数量：" + annotations.length);
        for (PDFAnnotation annotation : annotations) {
            int type = annotation.getType();
            pathList = new ArrayList<>();
            boolean areaHit = false;
            if (type == TYPE_LINE) {//直线
                Point[] line = annotation.getLine();
                path = new Path();
                path.moveTo(line[0].x, line[0].y);
                path.lineTo(line[1].x, line[1].y);
                pathList.add(path);
            } else if (type == TYPE_INK) {//墨迹
                Point[][] inkList = annotation.getInkList();
                for (Point[] points : inkList) {
                    float tempX = 0, tempY = 0;
                    int length = points.length;
                    for (int j = 0; j < length; j++) {
                        Point point = points[j];
                        System.out.print(point + "  ");
                        float x = point.x, y = point.y;
                        if (j == 0) {
                            path = new Path();
                            path.moveTo(x, y);
                            tempX = x;
                            tempY = y;
                        } else {
                            float dx = Math.abs(x - tempX), dy = Math.abs(tempY - y);
                            if (dx >= 1 || dy >= 1) {
                                path.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
                            }
                            tempX = x;
                            tempY = y;
                            if (j == length - 1) {
                                pathList.add(path);
                            }
                        }
                    }
                }
            } else if (type == TYPE_SCREEN) {
                Rect rect = annotation.getRect();
                path = new Path();
                RectF rectF = new RectF(rect.x0, rect.y0, rect.x1, rect.y1);
                path.addRect(rectF, Path.Direction.CCW);
                pathList.add(path);
            } else if (type == TYPE_HIGHLIGHT || type == TYPE_UNDERLINE || type == TYPE_STRIKE_OUT) {
                // 文本标记批注（下划线/删除线/高亮）：以 QuadPoints 生成矩形命中区域
                areaHit = true;
                if (annotation.hasQuadPoints()) {
                    Quad[] quads = annotation.getQuadPoints();
                    for (Quad quad : quads) {
                        Rect r = quad.toRect();
                        path = new Path();
                        path.addRect(new RectF(r.x0, r.y0, r.x1, r.y1), Path.Direction.CCW);
                        pathList.add(path);
                    }
                } else {
                    Rect rect = annotation.getRect();
                    path = new Path();
                    path.addRect(new RectF(rect.x0, rect.y0, rect.x1, rect.y1), Path.Direction.CCW);
                    pathList.add(path);
                }
            }
            Debugger.i(TAG, "annotations2path pathList=" + pathList.size());
            annotationPathBeans.add(new AnnotationPathBean(pathList, annotation, areaHit));
        }
        Debugger.i(TAG, "annotations2path annotationPathBeans=" + annotationPathBeans.size());
        return annotationPathBeans;
    }

    /**
     * 根据坐标点找到要删除的批注
     *
     * @param annotationPathBeans 当前页所有的批注,通过 {@link #annotations2path(PDFAnnotation[] annotations)} 方法获取
     * @param x                   转换后的x
     * @param y                   转换后的y
     * @return 要删除的批注 {@link PDFAnnotation}
     */
    public PDFAnnotation findShouldDeleteAnnotation(List<AnnotationPathBean> annotationPathBeans, float x, float y) {
        for (AnnotationPathBean annotationPathBean : annotationPathBeans) {
            PDFAnnotation pdfAnnotation = annotationPathBean.getPdfAnnotation();
            List<Path> pathList = annotationPathBean.getPaths();
            for (int i = 0; i < pathList.size(); i++) {
                Path path = pathList.get(i);
                if (annotationPathBean.isAreaHit()) {
                    // 区域型批注（下划线/删除线/高亮）：触摸点落在矩形内（含 ±20 容差）即命中
                    RectF bounds = new RectF();
                    path.computeBounds(bounds, true);
                    if (x >= bounds.left - 20 && x <= bounds.right + 20
                            && y >= bounds.top - 20 && y <= bounds.bottom + 20) {
                        return pdfAnnotation;
                    }
                } else {
                    PathMeasure pm = new PathMeasure(path, false);
                    float length = pm.getLength();
                    Path tempPath = new Path();
                    pm.getSegment(0, length, tempPath, false);
                    float[] fa = new float[2];
                    float sc = 0;
                    while (sc < 1) {
                        sc += 0.001;
                        pm.getPosTan(sc * length, fa, null);
                        if (Math.abs((int) fa[0] - (int) x) <= 20 && Math.abs((int) fa[1] - (int) y) <= 20) {
                            return pdfAnnotation;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 根据坐标点删除页面已有的批注
     *
     * @param pageIdx 页面索引
     * @param width   当前页面在屏幕上显示的像素宽度
     * @param height  当前页面在屏幕上显示的像素高度
     * @param x       页内文档坐标 x（已减去 pageTop，即相对页顶的屏幕 px）
     * @param y       页内文档坐标 y
     * @return true 表示删除了批注
     */
    public synchronized boolean deleteAnnotation(int pageIdx, int width, int height, float x, float y) {
        return deleteAnnotationWithOperationIds(pageIdx, width, height, x, y).length > 0;
    }

    /**
     * 根据坐标删除页面批注，并返回被删除批注对应的共享操作标识。
     * 对于没有共享标识的历史批注，数组中返回 0 表示已经删除但无法跨端同步。
     */
    public synchronized long[] deleteAnnotationWithOperationIds(int pageIdx, int width, int height,
                                                                float x, float y) {
        try {
            Page page = doc.loadPage(pageIdx);
            PDFPage pdfPage = (PDFPage) page;
            PDFAnnotation[] annotations = pdfPage.getAnnotations();
            if (annotations == null) return new long[0];
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            float pdfX = realWidth / width * x;
            float pdfY = realHeight / height * y;
            List<AnnotationPathBean> annotationPathBeans = annotations2path(annotations);
            PDFAnnotation pdfAnnotation = findShouldDeleteAnnotation(annotationPathBeans, pdfX, pdfY);
            if (pdfAnnotation != null) {
                Long operationId = operationIdByAnnotation.get(pdfAnnotation);
                if (operationId != null && operationId > 0) {
                    deleteAnnotationsByOperationIds(new long[]{operationId});
                    return new long[]{operationId};
                }
                pdfPage.deleteAnnotation(pdfAnnotation);
                unregisterAnnotation(pdfAnnotation);
                invalidatePageCache();
                return new long[]{0L};
            }
        } catch (Exception e) {
            Debugger.e(TAG + " deleteAnnotation Exception", e);
        }
        return new long[0];
    }

    public static class SharedAnnotationData {
        final long operationId;
        final int pageNumber;
        final int type;
        final float strokeWidth;
        final int argb;
        final float[] points;

        public SharedAnnotationData(long operationId, int pageNumber, int type,
                                    float strokeWidth, int argb, float[] points) {
            this.operationId = operationId;
            this.pageNumber = pageNumber;
            this.type = type;
            this.strokeWidth = strokeWidth;
            this.argb = argb;
            this.points = points;
        }
    }

    public String save(String srcPath, String saveDirPath) throws Exception {
        String fileNameNoExtension = Util.getFileNameNoExtension(srcPath);
        String destPath = saveDirPath + File.separator + fileNameNoExtension + ".pdf";
        destPath = Util.getUniqueFilePath(destPath);
        boolean copy = Util.copyFile(new File(srcPath), new File(destPath), new Util.OnReplaceListener() {
            @Override
            public boolean onReplace(File srcFile, File destFile) {
                Debugger.i(TAG, "onReplace: " + destFile.getAbsolutePath() + " 已存在，需要删除才能继续");
                return destFile.delete();
            }
        }, new Util.OnProgressUpdateListener() {
            @Override
            public void onProgressUpdate(double progress) {
                Debugger.i(TAG, "onProgressUpdate: " + progress);
            }
        });
        Debugger.i(TAG, "srcPath:" + srcPath + ",destPath:" + destPath + ",copy:" + copy);
        doc.saveDoc(destPath, "incremental");
        return destPath;
    }

    public synchronized void onDestroy() {
        Debugger.i(TAG, "onDestroy: start");
        try {
            if (page != null)
                page.destroy();
            page = null;
            if (doc != null) {
                Debugger.i(TAG, "onDestroy doc: start");
                doc.destroy();
                Debugger.i(TAG, "onDestroy doc: end");
            }
            doc = null;
            Debugger.i(TAG, "onDestroy: end");
        } catch (Exception e) {
            Debugger.e(e);
        }
    }

}
