package com.xlk.mupdf.library.bus;

import com.artifex.mupdf.fitz.Point;

/**
 * @author : Administrator
 * created on 2025/6/20 15:39
 */
public class MupdfAnnotationBean {
    int mediaId;
    int pageNum;
    int type;
    float paintSize;
    int paintColor;
    Point[] inkList;

    public MupdfAnnotationBean(int mediaId, int pageNum, int type, float paintSize, int paintColor, Point[] inkList) {
        this.mediaId = mediaId;
        this.pageNum = pageNum;
        this.type = type;
        this.paintSize = paintSize;
        this.paintColor = paintColor;
        this.inkList = inkList;
    }

    public int getMediaId() {
        return mediaId;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getType() {
        return type;
    }

    public float getPaintSize() {
        return paintSize;
    }

    public int getPaintColor() {
        return paintColor;
    }

    public Point[] getInkList() {
        return inkList;
    }
}
