package com.xlk.mupdf.library.bus;

import com.artifex.mupdf.fitz.Point;

/**
 * @author : Administrator
 * created on 2025/6/20 15:39
 */
public class MupdfAnnotationBean {
    int pageNum;
    int width;
    int height;
    int type;
    float paintSize;
    int paintColor;
    Point[] inkList;

    public MupdfAnnotationBean(int pageNum, int width, int height, int type, float paintSize, int paintColor, Point[] inkList) {
        this.pageNum = pageNum;
        this.width = width;
        this.height = height;
        this.type = type;
        this.paintSize = paintSize;
        this.paintColor = paintColor;
        this.inkList = inkList;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public float getPaintSize() {
        return paintSize;
    }

    public void setPaintSize(float paintSize) {
        this.paintSize = paintSize;
    }

    public int getPaintColor() {
        return paintColor;
    }

    public void setPaintColor(int paintColor) {
        this.paintColor = paintColor;
    }

    public Point[] getInkList() {
        return inkList;
    }

    public void setInkList(Point[] inkList) {
        this.inkList = inkList;
    }
}
