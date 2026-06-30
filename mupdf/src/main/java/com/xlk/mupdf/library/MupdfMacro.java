package com.xlk.mupdf.library;

import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : Administrator
 * created on 2025/6/20 15:22
 */
public class MupdfMacro {
    public static final String TAG = "MuPDF";

    //<editor-fold desc="功能开关">
    /**
     * 恒迅版本的图标不同
     */
    public static boolean isHengXunVersion = false;
    /**
     * 共享pdf批注开关，默认false
     */
    public static boolean shareAnnotationEnable = false;
    /**
     * 可删除之前的批注的开关，默认false
     */
    public static boolean delete_history_annotation = false;

    /**
     * <p>清晰度限制，取值范围[0,4]，小于0表示不限制</p>
     * {@link com.artifex.mupdf.viewer.PageView#maxSourceScale(PointF)}
     * <table border="1">
     * <caption>分辨率限制规则</caption>
     * <tr>
     *   <th>DPI 级别</th>
     *   <th>最大宽度</th>
     *   <th>最大高度</th>
     *   <th>说明</th>
     * </tr>
     * <tr>
     *   <td>DENSITY_XXXHIGH (640dpi+)</td>
     *   <td>3840</td>
     *   <td>2160</td>
     *   <td>4K 分辨率</td>
     * </tr>
     * <tr>
     *   <td>DENSITY_XXHIGH (480dpi)</td>
     *   <td>2560</td>
     *   <td>1440</td>
     *   <td>2K 分辨率</td>
     * </tr>
     * <tr>
     *   <td>DENSITY_XHIGH (320dpi)</td>
     *   <td>1920</td>
     *   <td>1080</td>
     *   <td>1080p 分辨率</td>
     * </tr>
     * <tr>
     *   <td>其他 DPI</td>
     *   <td>1280</td>
     *   <td>720</td>
     *   <td>720p 分辨率</td>
     * </tr>
     * </table>
     *
     * <p>内存不足时会进一步降级：</p>
     * <table>
     * <tr><th>内存状态</th><th>最大分辨率</th></tr>
     * <tr><td>正常</td><td>按上表规则</td></tr>
     * <tr><td>低内存</td><td>强制降为 720p</td></tr>
     * </table>
     */
    public static int clarityLimitMode = MupdfClarityMode.UNRESTRICTED;

    /**
     * <p>页面背景颜色（纸张色，护眼/夜间模式），默认白色 {@code 0xFFFFFFFF} 表示不改变。</p>
     * <p>浅色背景使用乘法着色，白底被染成纸张色且黑色文字保持不变；
     * 深色背景会反转前景，使白底映射为纸张色、黑色文字映射为白色。</p>
     * {@link #buildColorFilter()}
     */
    public static final int DEFAULT_BACKGROUND_COLOR = 0xFFFFFFFF;
    public static int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    public static final int MIN_BRIGHTNESS = -255;
    public static final int MAX_BRIGHTNESS = 255;
    /**
     * <p>窗口亮度，取值范围 [-255, 255]，{@code 0} 表示跟随系统亮度。</p>
     * <p>正值变亮、负值变暗。亮度由 Activity 窗口亮度实现，不参与页面颜色滤镜，避免 PDF 画面偏色失真。</p>
     */
    public static int brightness = 0;
    public static final int ZOOM_PERCENT_UNSET = -1;

    /**
     * 根据 {@link #backgroundColor} 构建纸张颜色滤镜。
     * <p>浅色背景按通道做乘法着色，深色背景同时反转前景；
     * 当背景为默认值时返回 {@code null}（即不做处理）。</p>
     *
     * @return {@link ColorMatrixColorFilter}，或在默认状态下返回 {@code null}
     */
    public static ColorMatrixColorFilter buildColorFilter() {
        if (backgroundColor == DEFAULT_BACKGROUND_COLOR) {
            return null;
        }
        float r = Color.red(backgroundColor) / 255f;
        float g = Color.green(backgroundColor) / 255f;
        float b = Color.blue(backgroundColor) / 255f;
        float t = 0f;
        // 深色纸张需要同时反转前景，否则黑色文字在深色背景上不可见。
        // 映射关系为：原始白色 -> 纸张色，原始黑色 -> 白色。
        boolean darkBackground = Color.red(backgroundColor) * 299
                + Color.green(backgroundColor) * 587
                + Color.blue(backgroundColor) * 114 < 128000;
        if (darkBackground) {
            r -= 1f;
            g -= 1f;
            b -= 1f;
            t = 255f;
        }
        float[] matrix = {
                r, 0, 0, 0, t,
                0, g, 0, 0, t,
                0, 0, b, 0, t,
                0, 0, 0, 1, 0
        };
        return new ColorMatrixColorFilter(matrix);
    }

    public static int clampBrightness(int value) {
        return Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, value));
    }

    //</editor-fold>

    //<editor-fold desc="bundle key值">
    public static final String mupdf_bundle_key = "mupdf_bundle";
    /**
     * 文件路径
     */
    public static final String bundle_key_file_path = "filePath";
    public static final String bundle_key_annotation_save_path = "savePath";
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
     * 水印字体颜色
     */
    public static final String bundle_key_watermark_color = "watermark_color";
    /**
     * 批注上传开关
     */
    public static final String bundle_key_upload_enable = "upload_enable";
    /**
     * 批注功能开关
     */
    public static final String bundle_key_annotation_enable = "annotation_enable";
    /**
     * 签名功能开关
     */
    public static final String bundle_key_signature_enable = "signature_enable";
    /**
     * 截图功能开关
     */
    public static final String bundle_key_capture_enable = "capture_enable";
    public static final String bundle_key_wps_open_enable = "wps_open_enable";
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
    public static final String bundle_key_page_index = "page_index";
    /**
     * 清晰度限制
     */
    public static final String bundle_key_clarityLimitMode = "clarityLimitMode";
    /**
     * 自动缩放成全屏
     */
    public static final String bundle_key_full_screen = "full_screen";
    /**
     * 页面背景颜色（纸张色）
     */
    public static final String bundle_key_background_color = "background_color";
    public static final String bundle_key_background_color_configured = "background_color_configured";
    /**
     * 窗口亮度
     */
    public static final String bundle_key_brightness = "brightness";
    public static final String bundle_key_brightness_configured = "brightness_configured";
    public static final String bundle_key_zoom_percent = "zoom_percent";
    public static final String bundle_key_zoom_percent_configured = "zoom_percent_configured";
    //</editor-fold>

    //<editor-fold desc="共享批注相关">

    /**
     * 所有正在参与共享绘制的设备id
     */
    public static List<Integer> sharingIds = new ArrayList<>();

    public static boolean isSharing;
    public static long launchSrcwbid;//发起人的白板标识 取微秒级的时间作标识 白板标识使用
    public static int launchSrcmemid;//发起人的人员ID
    //</editor-fold>

}
