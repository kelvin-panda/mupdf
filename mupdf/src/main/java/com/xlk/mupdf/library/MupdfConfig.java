package com.xlk.mupdf.library;

import android.graphics.Color;

/**
 * 宏开关
 *
 * @author : Administrator
 * @date : 2023/5/12 14:03
 */
public class MupdfConfig {
    private final String filePath;
    private final String annotationSaveDirPath;
    private final String fileUri;
    private final int mediaId;
    private final boolean watermarkEnable;
    private final String watermarkContent;
    private final int watermarkColor;
    private final boolean deleteSourceFile;
    private final boolean uploadEnable;
    private final boolean annotationEnable;
    private final boolean signatureEnable;
    private final boolean captureEnable;
    private final boolean wpsOpenEnable;
    private final int uploadDirId;
    private final boolean onlyPreview;
    private final int pageIndex;

    public MupdfConfig(MupdfConfig.Builder builder) {
        this.filePath = builder.filePath;
        this.annotationSaveDirPath = builder.annotationSaveDirPath;
        this.fileUri = builder.fileUri;
        this.mediaId = builder.mediaId;
        this.watermarkEnable = builder.watermarkEnable;
        this.watermarkContent = builder.watermarkContent;
        this.watermarkColor = builder.watermarkColor;
        this.deleteSourceFile = builder.deleteSourceFile;
        this.uploadEnable = builder.uploadEnable;
        this.annotationEnable = builder.annotationEnable;
        this.signatureEnable = builder.signatureEnable;
        this.captureEnable = builder.captureEnable;
        this.wpsOpenEnable = builder.wpsOpenEnable;
        this.uploadDirId = builder.uploadDirId;
        this.onlyPreview = builder.onlyPreview;
        this.pageIndex = builder.pageIndex;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getAnnotationSaveDirPath() {
        return annotationSaveDirPath;
    }

    public String getFileUri() {
        return fileUri;
    }

    public int getMediaId() {
        return mediaId;
    }

    public boolean isWatermarkEnable() {
        return watermarkEnable;
    }

    public String getWatermarkContent() {
        return watermarkContent;
    }

    public int getWatermarkColor() {
        return watermarkColor;
    }

    public boolean isDeleteSourceFile() {
        return deleteSourceFile;
    }

    public boolean isUploadEnable() {
        return uploadEnable;
    }

    public boolean isAnnotationEnable() {
        return annotationEnable;
    }

    public boolean isSignatureEnable() {
        return signatureEnable;
    }

    public boolean isCaptureEnable() {
        return captureEnable;
    }


    public boolean isWpsOpenEnable() {
        return wpsOpenEnable;
    }

    public int getUploadDirId() {
        return uploadDirId;
    }

    public boolean isOnlyPreview() {
        return onlyPreview;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    @Override
    public String toString() {
        return "MupdfConfig{" +
                "filePath='" + filePath + '\'' +
                ", fileUri='" + fileUri + '\'' +
                ", mediaId=" + mediaId +
                ", watermarkEnable=" + watermarkEnable +
                ", watermarkContent='" + watermarkContent + '\'' +
                ", deleteSourceFile=" + deleteSourceFile +
                ", uploadEnable=" + uploadEnable +
                ", annotationEnable=" + annotationEnable +
                ", signatureEnable=" + signatureEnable +
                ", captureEnable=" + captureEnable +
                ", wpsOpenEnable=" + wpsOpenEnable +
                ", uploadDirId=" + uploadDirId +
                ", onlyPreview=" + onlyPreview +
                ", pageIndex=" + pageIndex +
                '}';
    }

    public static class Builder {
        private String filePath = "";
        private String annotationSaveDirPath = "";
        private String fileUri = "";
        private int mediaId = 0;
        private boolean watermarkEnable = false;
        private String watermarkContent = "";
        private int watermarkColor = Color.parseColor("#66000000");
        private boolean deleteSourceFile = false;
        private boolean uploadEnable = true;
        private boolean annotationEnable = true;
        private boolean signatureEnable = true;
        private boolean captureEnable = true;
        private boolean wpsOpenEnable = true;
        private int uploadDirId = 2;
        private boolean onlyPreview = false;
        private int pageIndex = 0;

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder annotationSaveDirPath(String annotationSaveDirPath) {
            this.annotationSaveDirPath = annotationSaveDirPath;
            return this;
        }

        public Builder fileUri(String fileUri) {
            this.fileUri = fileUri;
            return this;
        }

        public Builder mediaId(int mediaId) {
            this.mediaId = mediaId;
            return this;
        }

        public Builder watermarkEnable(boolean watermarkEnable) {
            this.watermarkEnable = watermarkEnable;
            return this;
        }

        public Builder watermarkContent(String watermark) {
            this.watermarkContent = watermark;
            return this;
        }

        public Builder watermarkColor(int color) {
            this.watermarkColor = color;
            return this;
        }

        public Builder deleteSourceFile(boolean deleteSourceFile) {
            this.deleteSourceFile = deleteSourceFile;
            return this;
        }

        public Builder uploadEnable(boolean uploadEnable) {
            this.uploadEnable = uploadEnable;
            return this;
        }

        public Builder annotationEnable(boolean annotationEnable) {
            this.annotationEnable = annotationEnable;
            return this;
        }

        public Builder signatureEnable(boolean signatureEnable) {
            this.signatureEnable = signatureEnable;
            return this;
        }

        public Builder captureEnable(boolean captureEnable) {
            this.captureEnable = captureEnable;
            return this;
        }

        public Builder wpsOpenEnable(boolean wpsOpenEnable) {
            this.wpsOpenEnable = wpsOpenEnable;
            return this;
        }

        public Builder uploadDirId(int uploadDirId) {
            this.uploadDirId = uploadDirId;
            return this;
        }

        public Builder onlyPreview(boolean onlyPreview) {
            this.onlyPreview = onlyPreview;
            return this;
        }

        public Builder pageIndex(int pageIndex) {
            this.pageIndex = pageIndex;
            return this;
        }

        public MupdfConfig build() {
            return new MupdfConfig(this);
        }
    }
}
