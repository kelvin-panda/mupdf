package com.xlk.mupdf.library;

import androidx.annotation.Nullable;

import com.xlk.mupdf.library.api.MupdfHost;

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
    private final int clarityLimitMode;
    private final boolean fullScreenEnable;
    private final int backgroundColor;
    private final int brightness;
    private final int zoomPercent;
    private final boolean windowWatermarkEnable;
    private final String windowWatermarkContent;
    private final int windowWatermarkColor;
    private final boolean backgroundColorConfigured;
    private final boolean brightnessConfigured;
    private final boolean zoomPercentConfigured;
    private final boolean signatureFormEnabled;
    private final boolean fillInSignatureEnabled;
    private final boolean annotationInputTextEnabled;
    private final boolean backButtonEnabled;
    private final boolean informSignature;
    private final boolean shouldSignature;
    /**
     * 会话标识，用于并存会话的路由。见 {@link com.xlk.mupdf.library.api.MupdfSession#id()}。
     */
    private final String sessionId;
    /**
     * 库 → 宿主的能力端口，取代原先的 EventBus 出站事件。
     */
    private final MupdfHost host;
    private final boolean shareAnnotationEnabled;
    private final boolean shareAnnotationConfigured;
    private final boolean hengXunStyle;
    private final boolean hengXunStyleConfigured;

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
        this.clarityLimitMode = builder.clarityLimitMode;
        this.fullScreenEnable = builder.fullScreenEnable;
        this.backgroundColor = builder.backgroundColor;
        this.brightness = builder.brightness;
        this.zoomPercent = builder.zoomPercent;
        this.windowWatermarkEnable = builder.windowWatermarkEnable;
        this.windowWatermarkContent = builder.windowWatermarkContent;
        this.windowWatermarkColor = builder.windowWatermarkColor;
        this.backgroundColorConfigured = builder.backgroundColorConfigured;
        this.brightnessConfigured = builder.brightnessConfigured;
        this.zoomPercentConfigured = builder.zoomPercentConfigured;
        this.signatureFormEnabled = builder.signatureFormEnabled;
        this.fillInSignatureEnabled = builder.fillInSignatureEnabled;
        this.annotationInputTextEnabled = builder.annotationInputTextEnabled;
        this.backButtonEnabled = builder.backButtonEnabled;
        this.informSignature = builder.informSignature;
        this.shouldSignature = builder.shouldSignature;
        this.sessionId = builder.sessionId;
        this.host = builder.host;
        this.shareAnnotationEnabled = builder.shareAnnotationEnabled;
        this.shareAnnotationConfigured = builder.shareAnnotationConfigured;
        this.hengXunStyle = builder.hengXunStyle;
        this.hengXunStyleConfigured = builder.hengXunStyleConfigured;
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

    public int getClarityLimitMode() {
        return clarityLimitMode;
    }

    public boolean isFullScreenEnable() {
        return fullScreenEnable;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getBrightness() {
        return brightness;
    }

    public int getZoomPercent() {
        return zoomPercent;
    }

    public boolean isBackgroundColorConfigured() {
        return backgroundColorConfigured;
    }

    public boolean isBrightnessConfigured() {
        return brightnessConfigured;
    }

    public boolean isZoomPercentConfigured() {
        return zoomPercentConfigured;
    }

    public boolean isWindowWatermarkEnable() {
        return windowWatermarkEnable;
    }

    public String getWindowWatermarkContent() {
        return windowWatermarkContent;
    }

    public int getWindowWatermarkColor() {
        return windowWatermarkColor;
    }

    public boolean isSignatureFormEnabled() {
        return signatureFormEnabled;
    }

    public boolean isFillInSignatureEnabled() {
        return fillInSignatureEnabled;
    }

    public boolean isAnnotationInputTextEnabled() {
        return annotationInputTextEnabled;
    }

    public boolean isBackButtonEnabled() {
        return backButtonEnabled;
    }

    public boolean isInformSignature() {
        return informSignature;
    }

    public boolean isShouldSignature() {
        return shouldSignature;
    }

    /**
     * 会话标识。宿主不设置时，库会按 {@code "mupdf-" + 自增序号} 自动生成一个并打告警日志。
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 库 → 宿主的能力端口，详见 {@link MupdfHost}。
     */
    @Nullable
    public MupdfHost getHost() {
        return host;
    }

    /**
     * 是否显示"邀请他人批注"入口（原 {@code MupdfMacro.shareAnnotationEnable}）。
     */
    public boolean isShareAnnotationEnabled() {
        return shareAnnotationEnabled;
    }

    /**
     * 宿主是否显式配置过 {@link #isShareAnnotationEnabled()}。
     * 未配置时库回退读 {@code MupdfMacro.shareAnnotationEnable}，保证 6.x 行为不变。
     */
    public boolean isShareAnnotationConfigured() {
        return shareAnnotationConfigured;
    }

    /**
     * 是否使用恒迅版界面布局（原 {@code MupdfMacro.isHengXunVersion}）。
     */
    public boolean isHengXunStyle() {
        return hengXunStyle;
    }

    /**
     * 宿主是否显式配置过 {@link #isHengXunStyle()}。
     * 未配置时库回退读 {@code MupdfMacro.isHengXunVersion}，保证 6.x 行为不变。
     */
    public boolean isHengXunStyleConfigured() {
        return hengXunStyleConfigured;
    }

    @Override
    public String toString() {
        return "MupdfConfig{" +
                "filePath='" + filePath + '\'' +
                ", annotationSaveDirPath='" + annotationSaveDirPath + '\'' +
                ", fileUri='" + fileUri + '\'' +
                ", mediaId=" + mediaId +
                ", watermarkEnable=" + watermarkEnable +
                ", watermarkContent='" + watermarkContent + '\'' +
                ", watermarkColor=" + watermarkColor +
                ", deleteSourceFile=" + deleteSourceFile +
                ", uploadEnable=" + uploadEnable +
                ", annotationEnable=" + annotationEnable +
                ", signatureEnable=" + signatureEnable +
                ", captureEnable=" + captureEnable +
                ", wpsOpenEnable=" + wpsOpenEnable +
                ", uploadDirId=" + uploadDirId +
                ", onlyPreview=" + onlyPreview +
                ", pageIndex=" + pageIndex +
                ", clarityLimitMode=" + clarityLimitMode +
                ", fullScreenEnable=" + fullScreenEnable +
                ", backgroundColor=" + backgroundColor +
                ", brightness=" + brightness +
                ", zoomPercent=" + zoomPercent +
                ", windowWatermarkEnable=" + windowWatermarkEnable +
                ", windowWatermarkContent='" + windowWatermarkContent + '\'' +
                ", windowWatermarkColor=" + windowWatermarkColor +
                ", backgroundColorConfigured=" + backgroundColorConfigured +
                ", brightnessConfigured=" + brightnessConfigured +
                ", zoomPercentConfigured=" + zoomPercentConfigured +
                ", signatureFormEnabled=" + signatureFormEnabled +
                ", fillInSignatureEnabled=" + fillInSignatureEnabled +
                ", annotationInputTextEnabled=" + annotationInputTextEnabled +
                ", backButtonEnabled=" + backButtonEnabled +
                ", informSignature=" + informSignature +
                ", shouldSignature=" + shouldSignature +
                ", sessionId='" + sessionId + '\'' +
                ", host=" + (host == null ? "null" : host.getClass().getName()) +
                ", shareAnnotationEnabled=" + shareAnnotationEnabled +
                ", shareAnnotationConfigured=" + shareAnnotationConfigured +
                ", hengXunStyle=" + hengXunStyle +
                ", hengXunStyleConfigured=" + hengXunStyleConfigured +
                '}';
    }

    public static class Builder {
        private String filePath = "";
        private String annotationSaveDirPath = "";
        private String fileUri = "";
        private int mediaId = 0;
        private boolean watermarkEnable = false;
        private String watermarkContent = "";
        private int watermarkColor = MupdfMacro.DEFAULT_WATERMARK_COLOR;
        /**
         * 退出时是否删除源文件
         */
        private boolean deleteSourceFile = false;
        /**
         * 批注上传开关【默认显示】
         */
        private boolean uploadEnable = true;
        /**
         * 批注控件【默认显示】
         */
        private boolean annotationEnable = true;
        /**
         * 签名控件【默认显示】
         */
        private boolean signatureEnable = true;
        /**
         * 截图图标【默认显示】
         */
        private boolean captureEnable = true;
        /**
         * 外部打开图标【默认显示】
         */
        private boolean wpsOpenEnable = true;
        /**
         * 批注上传的目录id【默认2】
         */
        private int uploadDirId = MupdfMacro.DEFAULT_UPLOAD_DIR_ID;
        /**
         * 只进行预览不做任何操作
         */
        private boolean onlyPreview = false;
        /**
         * 打开后的起始页
         */
        private int pageIndex = 0;
        /**
         * 加载时的清晰度【默认不限制】
         */
        private int clarityLimitMode = MupdfClarityMode.UNRESTRICTED;
        /**
         * 打开后是否全屏
         */
        private boolean fullScreenEnable = true;
        private int backgroundColor = MupdfMacro.DEFAULT_BACKGROUND_COLOR;
        private int brightness = 0;
        private int zoomPercent = MupdfMacro.ZOOM_PERCENT_UNSET;
        private boolean windowWatermarkEnable = false;
        private String windowWatermarkContent = "";
        private int windowWatermarkColor = MupdfMacro.DEFAULT_WINDOW_WATERMARK_COLOR;
        private boolean backgroundColorConfigured = false;
        private boolean brightnessConfigured = false;
        private boolean zoomPercentConfigured = false;
        private boolean signatureFormEnabled = false;
        private boolean fillInSignatureEnabled = false;
        private boolean annotationInputTextEnabled = false;
        private boolean backButtonEnabled = false;
        private boolean informSignature = false;
        /**
         * 参会端收到秘书的签名通知时并未打开打开文件，在下载完毕进行打开时设置为true
         */
        private boolean shouldSignature = false;
        private String sessionId = "";
        private MupdfHost host = null;
        private boolean shareAnnotationEnabled = false;
        private boolean shareAnnotationConfigured = false;
        private boolean hengXunStyle = false;
        private boolean hengXunStyleConfigured = false;

        /**
         * 设置PDF预览文件（文件绝对路径）。
         * 同时设置 {@link #fileUri(String)} 时，打开优先使用 filePath。
         *
         * @param filePath 文件全路径
         */
        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * 设置PDF预览文件（content uri 等）。
         * 仅在 filePath 为空时作为兜底使用。
         *
         * @param fileUri 文件uri
         */
        public Builder fileUri(String fileUri) {
            this.fileUri = fileUri;
            return this;
        }

        /**
         * 设置文件媒体ID，共享批注时使用
         *
         * @param mediaId 媒体文件id
         */
        public Builder mediaId(int mediaId) {
            this.mediaId = mediaId;
            return this;
        }

        /**
         * 设置批注后保存的目录
         *
         * @param annotationSaveDirPath 目录路径
         */
        public Builder annotationSaveDirPath(String annotationSaveDirPath) {
            this.annotationSaveDirPath = annotationSaveDirPath;
            return this;
        }

        /**
         * 设置pdf水印开关
         *
         * @param watermarkEnable true or false
         */
        public Builder watermarkEnable(boolean watermarkEnable) {
            this.watermarkEnable = watermarkEnable;
            return this;
        }

        /**
         * 设置pdf水印内容
         */
        public Builder watermarkContent(String watermark) {
            this.watermarkContent = watermark;
            return this;
        }

        public Builder watermarkColor(int color) {
            this.watermarkColor = color;
            return this;
        }

        /**
         * 设置退出预览后是否删除源文件
         *
         * @param deleteSourceFile true or false
         */
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

        public Builder clarityLimitMode(@MupdfClarityMode int clarityLimitMode) {
            this.clarityLimitMode = clarityLimitMode;
            return this;
        }

        public Builder fullScreenEnable(boolean fullScreenEnable) {
            this.fullScreenEnable = fullScreenEnable;
            return this;
        }

        /**
         * 设置页面背景颜色（纸张色，护眼/夜间模式）。
         * 默认白色 {@code 0xFFFFFFFF} 表示不改变。
         *
         * @param backgroundColor ARGB 颜色值
         */
        public Builder backgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            this.backgroundColorConfigured = true;
            return this;
        }

        /**
         * 设置阅读器窗口亮度，取值范围 [-255, 255]，{@code 0} 表示跟随系统亮度。
         * 正值变亮、负值变暗，不修改 PDF 页面位图颜色。
         *
         * @param brightness 亮度值
         */
        public Builder brightness(int brightness) {
            this.brightness = MupdfMacro.clampBrightness(brightness);
            this.brightnessConfigured = true;
            return this;
        }

        /**
         * 设置初始缩放百分比。100 表示适宽；传入小于 0 表示不指定，由阅读器使用上次设置或默认单页完整显示。
         *
         * @param zoomPercent 缩放百分比
         */
        public Builder zoomPercent(int zoomPercent) {
            this.zoomPercent = zoomPercent;
            this.zoomPercentConfigured = zoomPercent >= 0;
            return this;
        }

        /**
         * 设置仅显示在阅读窗口上的水印，不写入 PDF 内容，也不会随保存导出。
         *
         * @param enable 是否显示窗口水印
         */
        public Builder windowWatermarkEnable(boolean enable) {
            this.windowWatermarkEnable = enable;
            return this;
        }

        /**
         * 设置窗口水印文字，仅在阅读器界面显示。
         *
         * @param content 水印文字
         */
        public Builder windowWatermarkContent(String content) {
            this.windowWatermarkContent = content == null ? "" : content;
            return this;
        }

        /**
         * 设置窗口水印颜色，建议使用带透明度的 ARGB 颜色。
         *
         * @param color ARGB 颜色值
         */
        public Builder windowWatermarkColor(int color) {
            this.windowWatermarkColor = color;
            return this;
        }

        /**
         * 签名表开关
         *
         * @param enable true or false
         */
        public Builder signatureForm(boolean enable) {
            this.signatureFormEnabled = enable;
            return this;
        }

        /**
         * 填写签名
         *
         * @param enable true or false
         */
        public Builder fillInSignature(boolean enable) {
            this.fillInSignatureEnabled = enable;
            return this;
        }

        /**
         * 批注输入文本开关
         *
         * @param enable true or false
         */
        public Builder annotationInputText(boolean enable) {
            this.annotationInputTextEnabled = enable;
            return this;
        }

        public Builder backButtonEnabled(boolean enable) {
            this.backButtonEnabled = enable;
            return this;
        }

        public Builder informSignature(boolean enable) {
            this.informSignature = enable;
            return this;
        }

        /**
         * @param shouldSignature 参会端收到秘书的签名通知时并未打开文件，在下载完毕进行打开时设置为true
         */
        public Builder shouldSignature(boolean shouldSignature) {
            this.shouldSignature = shouldSignature;
            return this;
        }

        /**
         * 设置会话标识，用于并存会话的定向路由。
         *
         * <p>7.0.0 起库不再使用全局 EventBus 广播，每个阅读会话需要唯一标识。
         * 不设置时库会自动生成并打告警日志，但宿主就无法用
         * {@link com.xlk.mupdf.library.api.MupdfSessions#find(String)} 找回句柄。</p>
         *
         * <p>建议用业务主键或 {@code 文件路径 + 时间戳}，保证宿主进程内唯一。</p>
         *
         * @param sessionId 会话标识
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId == null ? "" : sessionId;
            return this;
        }

        /**
         * 注入库 → 宿主的能力端口，取代原先的 EventBus 事件订阅。
         *
         * @param host 端口实现，可为单例；库只在会话期间持有引用
         * @see MupdfHost
         */
        public Builder host(MupdfHost host) {
            this.host = host;
            return this;
        }

        /**
         * 是否显示"邀请他人批注"入口。
         *
         * <p>不调用本方法时，库回退读取 {@code MupdfMacro.shareAnnotationEnable}，
         * 与 6.x 行为一致。</p>
         *
         * @param enable true or false
         */
        public Builder shareAnnotationEnabled(boolean enable) {
            this.shareAnnotationEnabled = enable;
            this.shareAnnotationConfigured = true;
            return this;
        }

        /**
         * 是否使用恒迅版界面布局。
         *
         * <p>不调用本方法时，库回退读取 {@code MupdfMacro.isHengXunVersion}，
         * 与 6.x 行为一致。</p>
         *
         * @param enable true or false
         */
        public Builder hengXunStyle(boolean enable) {
            this.hengXunStyle = enable;
            this.hengXunStyleConfigured = true;
            return this;
        }

        public MupdfConfig build() {
            return new MupdfConfig(this);
        }
    }
}
