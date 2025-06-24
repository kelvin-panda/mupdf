package com.xlk.mupdf.library.bus;

/**
 * @author : Administrator
 * created on 2024/6/25 18:49
 */
public class MupdfBusType {
    /**
     * 通知截图
     * <li>String mDocTitle</li>
     * <li>int 0</li>
     *
     */
    public static final String inform_screenshot = "inform_screenshot";
    /**
     * 文件上传
     * <li>String filePath</li>
     * <li>int dirId</li>
     *
     */
    public static final String inform_upload = "inform_upload";
    /**
     * 通知外部打开pdf文件
     * <li>String filePath</li>
     */
    public static final String out_open_inform = "out_open_inform";

    //<editor-fold desc="共享批注相关">
    /**
     * 通知本机前端进行邀请其他人批注pdf
     * <li>文件媒体id</li>
     * <li>页码</li>
     */
    public static final String inform_invite_annotation = "inform_invite_annotation";
    /**
     * 接收到其他人加入的通知
     * <li>参会人id</li>
     * <li>参会人名称</li>
     */
    public static final String receive_invite_annotation = "receive_invite_annotation";
    /**
     * 接收到其他人拒绝的通知
     */
    public static final String receive_reject_annotation = "receive_reject_annotation";
    /**
     * 接收到其他人退出的通知
     */
    public static final String receive_exit_annotation = "receive_exit_annotation";
    /**
     * 收到其他人的绘制信息
     * <li>参会人员id</li>
     * <li>页码和绘制信息</li>
     */
    public static final String receive_annotation_info = "receive_annotation_info";
    /**
     * 通知本机前端共享自己的批注出去 {@link MupdfAnnotationBean}
     */
    public static final String inform_share_annotation = "inform_share_annotation";
    /**
     * 通知自己退出PDF批注，jni层调用
     */
    public static final String inform_exit_annotation = "inform_exit_annotation";
    //</editor-fold>
}
