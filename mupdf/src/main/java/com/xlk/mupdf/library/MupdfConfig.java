package com.xlk.mupdf.library;

/**
 * 宏开关
 *
 * @author : Administrator
 * @date : 2023/5/12 14:03
 */
public class MupdfConfig {
    private final String filePath;
    private final String fileUri;
    private final int mediaId;
    private final boolean watermarkEnable;
    private final String watermarkContent;
    private final boolean deleteSourceFile;
    private final boolean uploadEnable;
    private final int uploadDirId;
    private final boolean onlyPreview;
    private final int pageIndex;

    public MupdfConfig(MupdfConfig.Builder builder) {
        this.filePath = builder.filePath;
        this.fileUri = builder.fileUri;
        this.mediaId = builder.mediaId;
        this.watermarkEnable = builder.watermarkEnable;
        this.watermarkContent = builder.watermarkContent;
        this.deleteSourceFile = builder.deleteSourceFile;
        this.uploadEnable = builder.uploadEnable;
        this.uploadDirId = builder.uploadDirId;
        this.onlyPreview = builder.onlyPreview;
        this.pageIndex = builder.pageIndex;
    }

    public String getFilePath() {
        return filePath;
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

    public boolean isDeleteSourceFile() {
        return deleteSourceFile;
    }

    public boolean isUploadEnable() {
        return uploadEnable;
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
                ", uploadDirId=" + uploadDirId +
                ", onlyPreview=" + onlyPreview +
                ", pageIndex=" + pageIndex +
                '}';
    }

    public static class Builder {
        private String filePath = "";
        private String fileUri = "";
        private int mediaId = 0;
        private boolean watermarkEnable = false;
        private String watermarkContent = "";
        private boolean deleteSourceFile = false;
        private boolean uploadEnable = true;
        private int uploadDirId = 2;
        private boolean onlyPreview = false;
        private int pageIndex = 0;

        public Builder filePath(String filePath) {
            this.filePath = filePath;
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

        public Builder deleteSourceFile(boolean deleteSourceFile) {
            this.deleteSourceFile = deleteSourceFile;
            return this;
        }

        public Builder uploadEnable(boolean uploadEnable) {
            this.uploadEnable = uploadEnable;
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
