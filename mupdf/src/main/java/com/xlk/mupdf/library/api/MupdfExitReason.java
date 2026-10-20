package com.xlk.mupdf.library.api;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 会话结束原因，随 {@link MupdfHost#onSessionClosed(String, int)} 回传。
 *
 * @author : Administrator
 */
@IntDef({
        MupdfExitReason.UNKNOWN,
        MupdfExitReason.EXITED_UNSAVED,
        MupdfExitReason.SAVED,
        MupdfExitReason.SAVE_FAILED,
        MupdfExitReason.OPEN_FAILED,
        MupdfExitReason.OPEN_EXTERNAL,
        MupdfExitReason.CLOSED_BY_HOST,
        MupdfExitReason.PASSWORD_CANCELLED,
        MupdfExitReason.REPLACED
})
@Retention(RetentionPolicy.SOURCE)
public @interface MupdfExitReason {
    /** 未明确原因（如被系统回收）。 */
    int UNKNOWN = 0;
    /** 用户退出且未保存（含保存提示里选"不保存"）。 */
    int EXITED_UNSAVED = 1;
    /** 已保存成功（此前已回调 {@link MupdfHost#onUploadRequested(String, int)}）。 */
    int SAVED = 2;
    /** 保存失败。 */
    int SAVE_FAILED = 3;
    /** 文件无法打开，用户点"确定"（此前已回调 {@link MupdfHost#onOpenExternally(String, String)}）。 */
    int OPEN_FAILED = 4;
    /** 用户选择外部应用打开（此前已回调 {@link MupdfHost#onOpenExternally(String, String)}）。 */
    int OPEN_EXTERNAL = 5;
    /** 宿主通过 {@link MupdfSession#close()} 请求关闭。 */
    int CLOSED_BY_HOST = 6;
    /** 密码输入被取消。 */
    int PASSWORD_CANCELLED = 7;
    /** 同一阅读器被新的打开请求替换（旧会话结束，新会话以新 sessionId 启动）。 */
    int REPLACED = 8;
}
