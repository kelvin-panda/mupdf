package com.xlk.mupdf.library.bus;

import com.artifex.mupdf.fitz.Point;

/**
 * @author : Administrator
 * created on 2025/6/23 17:56
 */
public class MupdfInkBean {
    /**
     * 页码：索引+1
     */
    int pageNumber;
    int type;
    int linesize;
    int argb;
    /**
     * 坐标值为基于文档的实际大小的百分比
     */
    Point[] array;

    public MupdfInkBean(int pageNumber, int type, int linesize, int argb, Point[] array) {
        this.pageNumber = pageNumber;
        this.type = type;
        this.linesize = linesize;
        this.argb = argb;
        this.array = array;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getType() {
        return type;
    }

    public int getLinesize() {
        return linesize;
    }

    public int getArgb() {
        return argb;
    }

    public Point[] getArray() {
        return array;
    }
}
