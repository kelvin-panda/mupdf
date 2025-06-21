package com.xlk.mupdf.library;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : Administrator
 * created on 2025/6/20 15:22
 */
public class MupdfMacro {
    //<editor-fold desc="bundle key值">
    public static final String mupdf_bundle_key = "mupdf_bundle";
    /**
     * 文件路径
     */
    public static final String bundle_key_file_path = "filePath";
    public static final String bundle_key_file_uri = "uri";
    public static final String bundle_key_file_mediaId = "mediaId";
    /**
     * 水印开关
     */
    public static final String bundle_key_watermark_enable = "watermark_enable";
    /**
     * 水印内容
     */
    public static final String bundle_key_watermark_content = "watermark_content";
    /**
     * 批注上传开关
     */
    public static final String bundle_key_upload_enable = "upload_enable";
    /**
     * 批注后上传的目录id
     */
    public static final String bundle_key_upload_dirId = "upload_dirId";
    /**
     * 退出时是否删除文件
     */
    public static final String bundle_key_delete_file = "delete_file";
    /**
     * 是否只预览
     */
    public static final String bundle_key_only_preview = "only_preview";
    /**
     * 打开时是否是共享状态
     */
    public static final String bundle_key_isSharing = "isSharing";
    //</editor-fold>

    //<editor-fold desc="共享批注相关">

    /**
     * 所有正在参与共享绘制的设备id
     */
    public static List<Integer> sharingIds = new ArrayList<>();

    public static boolean isSharing;
    public static int operid;
    public static long launchSrcwbid;//发起人的白板标识 取微秒级的时间作标识 白板标识使用
    public static int launchSrcmemid;//发起人的人员ID
    //</editor-fold>
}
