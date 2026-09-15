package com.xlk.mupdf.library.bus;

import com.artifex.mupdf.fitz.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author : Administrator
 * created on 2025/6/23 17:56
 */
public class MupdfInkBean {
    public static final int OP_ADD = 1;
    public static final int OP_ERASE = 2;
    public static final int OP_UNDO = 3;

    /**
     * 微秒级操作标识。
     */
    private long operationId;
    /**
     * {@link #OP_ADD}、{@link #OP_ERASE}、{@link #OP_UNDO}
     */
    private int operationType;
    /**
     * 页码：索引+1
     */
    @Deprecated
    private int pageNumber;
    /**
     * 批注类型 {@link com.artifex.mupdf.fitz.PDFAnnotation#TYPE_INK}
     */
    private int type;
    @Deprecated
    private int linesize;
    private float strokeWidth;
    private int argb;
    /**
     * 坐标值为基于文档的实际大小的百分比
     */
    @Deprecated
    private Point[] array;
    private List<InkSegment> segments;
    private long[] targetOperationIds;

    /**
     * 旧版本构造器，仅用于兼容依赖方。
     */
    @Deprecated
    public MupdfInkBean(int pageNumber, int type, int linesize, int argb, Point[] array) {
        this.pageNumber = pageNumber;
        this.type = type;
        this.linesize = linesize;
        this.strokeWidth = linesize;
        this.argb = argb;
        this.array = array;
        this.operationType = OP_ADD;
    }

    private MupdfInkBean(long operationId, int operationType, int type,
                         float strokeWidth, int argb, List<InkSegment> segments,
                         long[] targetOperationIds) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.type = type;
        this.strokeWidth = strokeWidth;
        this.argb = argb;
        this.segments = segments == null
                ? Collections.emptyList() : new ArrayList<>(segments);
        this.targetOperationIds = targetOperationIds == null
                ? new long[0] : targetOperationIds.clone();
    }

    public static MupdfInkBean createAdd(long operationId, int type, float strokeWidth,
                                         int argb, List<InkSegment> segments) {
        return new MupdfInkBean(operationId, OP_ADD, type, strokeWidth, argb,
                segments, null);
    }

    public static MupdfInkBean createErase(long operationId, long[] targetOperationIds) {
        return new MupdfInkBean(operationId, OP_ERASE, 0, 0f, 0,
                null, targetOperationIds);
    }

    public static MupdfInkBean createUndo(long operationId, long targetOperationId) {
        return new MupdfInkBean(operationId, OP_UNDO, 0, 0f, 0,
                null, new long[]{targetOperationId});
    }

    public long getOperationId() {
        return operationId;
    }

    public int getOperationType() {
        return operationType;
    }

    public float getStrokeWidth() {
        return strokeWidth > 0 ? strokeWidth : linesize;
    }

    public List<InkSegment> getSegments() {
        if (segments != null && !segments.isEmpty()) {
            return segments;
        }
        if (pageNumber > 0 && array != null && array.length > 0) {
            List<InkSegment> legacySegments = new ArrayList<>(1);
            legacySegments.add(new InkSegment(pageNumber, toFloatArray(array)));
            return legacySegments;
        }
        return Collections.emptyList();
    }

    public long[] getTargetOperationIds() {
        return targetOperationIds == null ? new long[0] : targetOperationIds.clone();
    }

    @Deprecated
    public int getPageNumber() {
        return pageNumber;
    }

    public int getType() {
        return type;
    }

    @Deprecated
    public int getLinesize() {
        return linesize;
    }

    public int getArgb() {
        return argb;
    }

    @Deprecated
    public Point[] getArray() {
        return array;
    }

    private static float[] toFloatArray(Point[] points) {
        float[] values = new float[points.length * 2];
        for (int i = 0; i < points.length; i++) {
            values[i * 2] = points[i].x;
            values[i * 2 + 1] = points[i].y;
        }
        return values;
    }

    public static class InkSegment {
        private final int pageNumber;
        /**
         * 交错存放的百分比坐标：[x0,y0,x1,y1...]
         */
        private final float[] points;

        public InkSegment(int pageNumber, float[] points) {
            this.pageNumber = pageNumber;
            this.points = points == null ? new float[0] : points.clone();
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public float[] getPoints() {
            return points.clone();
        }

        public int getPointCount() {
            return points.length / 2;
        }
    }
}
