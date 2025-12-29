package com.xlk.example;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * @author : Administrator
 * created on 2025/12/22 14:28
 */
public class WpsUtil {
    private static final String TAG = "WpsUtil";

    public static void openFile(Context context, String filePath) {
        openFile(context, new File(filePath));
    }

    public static void openFile(Context context, File file) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        if (!openWps(context, intent, file)) {
            openLocalFile(context, file);
        }
    }

    public static boolean openWps(Context context, Intent intent, File file) {
        boolean success = openWps(context, intent, file, true);
        if (!success) {
            Log.d(TAG, "WpsUtil.openWps: 未找到个人版的WPS软件，尝试使用专业版");
            success = openWps(context, intent, file, false);
            if (!success) {
                Log.d(TAG, "WpsUtil.openWps: 未找到专业版的WPS软件，使用wps打开失败");
            }
        }
        return success;
    }

    public static void openWps(Context context, Uri uri, boolean normal) {
        try {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            //如果是文档类文件并且不是pdf文件，设置只能使用WPS软件打开
            if (normal) {
                intent.setClassName(WpsModel.PackageName.NORMAL, WpsModel.ClassName.NORMAL);
            } else {
                intent.setClassName(WpsModel.PackageName.ENTERPRISE_PRO, WpsModel.ClassName.NORMAL);
            }
            Bundle bundle = WpsModel.getBundle(context);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uriForFile = uri.buildUpon().appendQueryParameter("timestamp", System.currentTimeMillis() + "").build();
            intent.setDataAndType(uriForFile, "application/pdf");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "openWps intent=" + intent);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openWps Exception: " + e);
        }
    }

    public static boolean openWps(Context context, Intent intent, File file, boolean normal) {
        //如果是文档类文件并且不是pdf文件，设置只能使用WPS软件打开
        if (normal) {
            intent.setClassName(WpsModel.PackageName.NORMAL, WpsModel.ClassName.NORMAL);
        } else {
            intent.setClassName(WpsModel.PackageName.ENTERPRISE_PRO, WpsModel.ClassName.NORMAL);
        }
        Bundle bundle = WpsModel.getBundle(context);
        intent.putExtras(bundle);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            //android 7.0以上时，URI不能直接暴露
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uriForFile = FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider", file)
                    .buildUpon().appendQueryParameter("timestamp", System.currentTimeMillis() + "").build();
            intent.setDataAndType(uriForFile, getMIMEType(file));
        } else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri = Uri.fromFile(file)
                    .buildUpon().appendQueryParameter("timestamp", System.currentTimeMillis() + "").build();
            intent.setDataAndType(uri, getMIMEType(file));
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "WpsUtil.openWps intent=" + intent + ",filePath=" + file.getAbsolutePath());
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "WpsUtil.openWps: " + e);
            return false;
        }
    }

    /**
     * 打开文件
     */
    public static void openLocalFile(Context context, File file) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //设置intent的Action属性
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri;
        //7.0以后，用了Content Uri 替换了原本的File Uri
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            //android 7.0以上时，URI不能直接暴露
            // 方式一
//            uri = getImageContentUri(context, file);
            // 方式二
            uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider", file).buildUpon().appendQueryParameter("timestamp", System.currentTimeMillis() + "").build();
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file).buildUpon().appendQueryParameter("timestamp", System.currentTimeMillis() + "").build();
        }
        //获取文件file的MIME类型
        String type = getMIMEType(file);
        //设置intent的data和Type属性。
        intent.setDataAndType(uri, type);
        //跳转
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "WpsUtil.openLocalFile: " + e);
        }
    }

    public static final String[][] MIME_MapTable = {
            //{后缀名， MIME类型}
            {".dwg", "application/acad"}, {".dxf", "application/dxf"},
            {".3gp", "video/3gpp"}, {".apk", "application/vnd.android.package-archive"}, {".asf", "video/x-ms-asf"},
            {".avi", "video/x-msvideo"}, {".bin", "application/octet-stream"}, {".bmp", "image/bmp"},
            {".c", "text/x-c"}, {".class", "application/octet-stream"}, {".conf", "text/plain"},
            {".cpp", "text/x-c++"}, {".doc", "application/msword"},
            {".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {".xls", "application/vnd.ms-excel"},
            {".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {".exe", "application/octet-stream"},
            {".caj", "application/x-caj"},
            {".gif", "image/gif"}, {".gtar", "application/x-gtar"},
            {".gz", "application/x-gzip"}, {".h", "text/plain"}, {".htm", "text/html"}, {".html", "text/html"},
            {".jar", "application/java-archive"}, {".java", "text/plain"}, {".jpeg", "image/jpeg"},
            {".jpg", "image/jpeg"}, {".js", "application/x-javascript"}, {".log", "text/plain"},
            {".m3u", "audio/x-mpegurl"}, {".m4a", "audio/mp4a-latm"}, {".m4b", "audio/mp4a-latm"},
            {".m4p", "audio/mp4a-latm"}, {".m4u", "video/vnd.mpegurl"}, {".m4v", "video/x-m4v"},
            {".mov", "video/quicktime"}, {".mp2", "audio/x-mpeg"}, {".mp3", "audio/x-mpeg"}, {".mp4", "video/mp4"},
            {".mpc", "application/vnd.mpohun.certificate"}, {".mpe", "video/mpeg"}, {".mpeg", "video/mpeg"},
            {".mpg", "video/mpeg"}, {".mpg4", "video/mp4"}, {".mpga", "audio/mpeg"},
            {".msg", "application/vnd.ms-outlook"}, {".ogg", "audio/ogg"}, {".pdf", "application/pdf"},
            {".png", "image/png"}, {".pps", "application/vnd.ms-powerpoint"},
            {".ppt", "application/vnd.ms-powerpoint"},
            {".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {".prop", "text/plain"}, {".rc", "text/plain"}, {".rmvb", "audio/x-pn-realaudio"},
            {".rtf", "application/rtf"}, {".sh", "text/plain"}, {".txt", "text/plain"},
            {".tar", "application/x-tar"}, {".tgz", "application/x-compressed"}, {".7z", "application/x-7z-compressed"},
            {".zip", "application/x-zip-compressed"}, {".wma", "audio/x-ms-wma"}, {".wmv", "audio/x-ms-wmv"},
            {".wps", "application/vnd.ms-works"}, {".xml", "text/plain"}, {".z", "application/x-compress"},
            {".md", "text/markdown"}, {".wav", "audio/x-wav"},
            {"", "*/*"}
    };

    public static String getMIMEType(String suffix) {
        String type = "*/*";
        if ("".equals(suffix)) {
            return type;
        }
        // 在MIME和文件类型的匹配表中找到对应的MIME类型。
        for (String[] strings : MIME_MapTable) {
            if (suffix.equals(strings[0])) {
                type = strings[1];
                break;
            }
        }
        return type;
    }

    /**
     * 得到文件类型
     *
     * @author 工藤一号 18883840501@163.com
     * @date 2017年6月8日  上午10:48:30
     */
    public static String getMIMEType(File file) {
        String type = "*/*";
        String fName = file.getName();
        // 获取后缀名前的分隔符"."在fName中的位置。
        int dotIndex = fName.lastIndexOf(".");
        if (dotIndex < 0) {
            return type;
        }
        /* 获取文件的后缀名 */
        String end = fName.substring(dotIndex).toLowerCase();
        if ("".equals(end)) {
            return type;
        }
        // 在MIME和文件类型的匹配表中找到对应的MIME类型。
        for (String[] strings : MIME_MapTable) {
            if (end.equals(strings[0])) {
                type = strings[1];
                break;
            }
        }
        return type;
    }
}
