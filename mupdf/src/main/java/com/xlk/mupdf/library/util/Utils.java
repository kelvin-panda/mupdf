package com.xlk.mupdf.library.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

/**
 * @author : Administrator
 * created on 2026/9/3 10:55
 */
public class Utils {

    /**
     * 将BitMap转为byte数组
     *
     */
    public static byte[] bmp2byte(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 50, baos);
        return baos.toByteArray();
    }

    public static Bitmap byte2bmp(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
