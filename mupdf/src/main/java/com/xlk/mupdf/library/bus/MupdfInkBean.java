package com.xlk.mupdf.library.bus;

import com.artifex.mupdf.fitz.Point;

/**
 * @author : Administrator
 * created on 2025/6/23 17:56
 */
public class MupdfInkBean {
    int pageindex;
    int type;
    int linesize;
    int argb;
    Point[] array;

    public MupdfInkBean(int pageindex, int type, int linesize, int argb, Point[] array) {
        this.pageindex = pageindex;
        this.type = type;
        this.linesize = linesize;
        this.argb = argb;
        this.array = array;
    }

    public int getPageindex() {
        return pageindex;
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
