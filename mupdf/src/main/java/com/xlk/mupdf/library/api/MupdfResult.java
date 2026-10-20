package com.xlk.mupdf.library.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次阅读会话的终态快照。
 *
 * <p>当前仅用于调试与日志输出；等 {@code MupdfLauncher}（ActivityResultContract 路径）
 * 落地后作为返回值使用。宿主若只实现 {@link MupdfHost}，不需要关心本类。</p>
 *
 * @author : Administrator
 * @since 7.0.0
 */
public final class MupdfResult {

    private final String sessionId;
    private final int reason;
    private final String savedPath;
    private final boolean documentModified;

    public MupdfResult(@NonNull String sessionId,
                       @MupdfExitReason int reason,
                       @Nullable String savedPath,
                       boolean documentModified) {
        this.sessionId = sessionId;
        this.reason = reason;
        this.savedPath = savedPath == null ? "" : savedPath;
        this.documentModified = documentModified;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    @MupdfExitReason
    public int getReason() {
        return reason;
    }

    /**
     * 保存后的文件路径，仅在 {@link MupdfExitReason#SAVED} 时非空。
     */
    @NonNull
    public String getSavedPath() {
        return savedPath;
    }

    /**
     * 本次会话是否修改过文档（有批注 / 水印 / 签名表等写入）。
     */
    public boolean isDocumentModified() {
        return documentModified;
    }

    @NonNull
    @Override
    public String toString() {
        return "MupdfResult{" +
                "sessionId='" + sessionId + '\'' +
                ", reason=" + reason +
                ", savedPath='" + savedPath + '\'' +
                ", documentModified=" + documentModified +
                '}';
    }
}
