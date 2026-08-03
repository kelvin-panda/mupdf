package com.xlk.mupdf.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.mupdf.annotation.AnnotationArtBoard;
import com.artifex.mupdf.annotation.AnnotationBean;
import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.util.ActUtil;
import com.artifex.mupdf.util.Debugger;
import com.artifex.mupdf.util.ScreenUtils;
import com.artifex.mupdf.util.Util;
import com.artifex.mupdf.viewer.ContentInputStream;
import com.artifex.mupdf.viewer.MuPDFCore;
import com.artifex.mupdf.viewer.OutlineActivity;
import com.artifex.mupdf.viewer.PageAdapter;
import com.artifex.mupdf.viewer.PageView;
import com.artifex.mupdf.viewer.Pallet;
import com.artifex.mupdf.viewer.ReaderView;
import com.artifex.mupdf.viewer.SearchTask;
import com.artifex.mupdf.viewer.SearchTaskResult;
import com.xlk.mupdf.library.bus.MupdfAnnotationBean;
import com.xlk.mupdf.library.bus.MupdfBusType;
import com.xlk.mupdf.library.bus.MupdfEventMessage;
import com.xlk.mupdf.library.bus.MupdfInkBean;
import com.xlk.mupdf.library.view.ArtBoardDialog;
import com.xlk.mupdf.library.view.MupdfColorPickerDialog;
import com.xlk.mupdf.library.view.MupdfColorPickerView;
import com.xlk.mupdf.library.view.ScalableView;
import com.xlk.mupdf.library.view.SignatureBoard;
import com.xlk.mupdf.library.view.WindowWatermarkView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.internal.CancelAdapt;

/**
 * <p>mupdf文档查看器</p>
 * 需要取消屏幕适配： AutoSizeConfig.getInstance().getExternalAdaptManager().addCancelAdaptOfActivity(MuPdfDocumentActivity.class);
 */
public class MuPdfDocumentActivity extends AppCompatActivity implements CancelAdapt {
    private static final String TAG = "MuPdfDocumentActivity";

    //<editor-fold desc="功能开关与接收参数">
    private boolean uploadEnable, annotationEnable, signatureEnable, captureEnable, wpsOpenEnable, deleteFileWhenExit,
            mWindowWatermarkEnabled, watermarkEnable, mSignatureFormEnabled, mfillSignatureFormEnabled, mAnnotationInputTextEnabled;
    private String srcFilePath, annotationSavePath, srcUri, mWatermark, mWindowWatermark;
    private boolean isFullScreen = true;
    private int mWatermarkColor, mWindowWatermarkColor;
    private int mediaId;
    /**
     * 只预览不可操作
     */
    private boolean isOnlyPreview;
    /**
     * 批注后上传的目录id
     */
    private int uploadDirId = 2;
    /**
     * 打开时所在的页码
     */
    private int srcPageIndex = 0;
    //</editor-fold>

    //<editor-fold desc="成员变量">

    private View viewTopAnnotation, viewTopSignature, viewTopScreenshot, viewTopRefresh, viewTopJump, viewTopBookmark, viewTopClose,//顶部控件
            viewArtClose, viewArtPen, viewArtLine, viewArtBrush, viewArtColor, viewArtHighlight, viewArtRevoke, viewArtDone,//画板控件
            viewArtInvite, viewArtUnderline, viewArtStrikeout, viewArtFreeText,
            viewTopWatermark, viewTopSignTable, viewTopSignRow, viewTopSearch, viewTopSetting;
    private boolean mAnnotationVisible, afterAnnotationRefresh;
    private AnnotationArtBoard artBoard;
    private TextView viewArtSizeTv;
    private SeekBar viewArtSeekBar;
    private final int default_ink_size = 3;
    /**
     * 提交和取消签名布局
     */
    private LinearLayout ll_signature_layout;
    private TextView tv_submit_signature, tv_cancel_signature;

    /**
     * 签名自定义View
     */
    private ScalableView mScalableView;
    private PageView mCurPageView;
    /**
     * 是否正在手写签名，正在签名时需要进行拦截翻页、缩放、顶部功能菜单显示
     */
    private boolean isSigning;

    /* The core rendering instance */
    enum TopBarMode {Main, Search, More}

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int OUTLINE_REQUEST = 0;
    private MuPDFCore core;
    private String mDocTitle;
    private String mDocKey;
    private ReaderView mDocView;
    private RelativeLayout rootView;
    private WindowWatermarkView mWindowWatermarkView;
    private View mButtonsView, viewTopThumbnail;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mDocNameView;
    private LinearLayout mLlPageView;
    private TextView mPageNumberView, mPrePageView, mNextPageView;
    private ViewAnimator mTopBarSwitcher, inkOperationSwitcher;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private AlertDialog.Builder mAlertBuilder;
    private ImageButton mSearchBack, mSearchFwd, mSearchClose;
    private EditText mSearchText;
    private SearchTask mSearchTask;
    private ArrayList<OutlineActivity.Item> mFlatOutline;
    private boolean mReturnToLibraryActivity = false;

    protected int mDisplayDPI;
    private int mLayoutEM = 10;
    private int mLayoutW = 312;
    private int mLayoutH = 504;


    //<editor-fold desc="缩略图相关">
    private DrawerLayout drawerLayout;
    private RecyclerView rvThumbnails;
    private ThumbnailAdapter thumbnailAdapter;
    //</editor-fold>

    protected View mLayoutButton;
    protected PopupMenu mLayoutPopupMenu;
    public static List<AnnotationBean> inkAnnotations = new ArrayList<>();
    /**
     * 有进行批注
     */
    private boolean hadAnnotation;
    private boolean hadAnnotationBeforeCurrentSession;

    /**
     * 即时保存模式：记录每笔保存的 pageIndex 列表，用于撤销时删除 PDF 注解
     */
    private final List<List<Integer>> savedAnnotationPages = new ArrayList<>();
    /**
     * 当前批注会话开始时的撤销栈位置；取消时只回滚本会话新增批注
     */
    private int annotationSessionStartIndex = 0;

    /**
     * 当前页：索引
     */
    private int currentPageIndex = 0;
    private int signTableTotalNames = 0;

    private Runnable pendingPageUpdate;
    private static final String DISPLAY_SETTINGS_PREFS = "mupdf_display_settings";
    private static final String PREF_BACKGROUND_COLOR = "background_color";
    private static final String PREF_BRIGHTNESS = "brightness";
    private static final String PREF_ZOOM_PERCENT = "zoom_percent";
    private static final int WATERMARK_MODE_NONE = 0;
    private static final int WATERMARK_MODE_PDF = 1;
    private static final int WATERMARK_MODE_WINDOW = 2;
    private int configuredZoomPercent = MupdfMacro.ZOOM_PERCENT_UNSET;

    private boolean isActivityAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed());
    }

    private void postToMain(Runnable runnable) {
        Handler handler = mainHandler;
        if (handler == null || runnable == null || !isActivityAlive()) return;
        handler.post(() -> {
            if (!isActivityAlive() || mainHandler == null) return;
            runnable.run();
        });
    }

    private void postToMainDelayed(Runnable runnable, long delayMillis) {
        Handler handler = mainHandler;
        if (handler == null || runnable == null || !isActivityAlive()) return;
        handler.postDelayed(() -> {
            if (!isActivityAlive() || mainHandler == null) return;
            runnable.run();
        }, delayMillis);
    }

    private void schedulePageUpdate(Runnable update) {
        Handler handler = mainHandler;
        if (handler == null || update == null || !isActivityAlive()) return;
        if (pendingPageUpdate != null) {
            handler.removeCallbacks(pendingPageUpdate);
        }
        pendingPageUpdate = () -> {
            if (!isActivityAlive() || mainHandler == null) return;
            update.run();
        };
        handler.postDelayed(pendingPageUpdate, 50);
    }

    private void scheduleAnnotationPagesUpdate(final List<Integer> pageIndexes) {
        schedulePageUpdate(() -> {
            if (mDocView == null || pageIndexes == null) return;
            for (int pageIdx : pageIndexes) {
                PageView pv = (PageView) mDocView.getView(pageIdx);
                if (pv != null) pv.update();
            }
        });
    }

    private void scheduleAnnotationPagesUpdateWithRestore(final List<Integer> pageIndexes,
                                                          final int anchorPageIndex,
                                                          final int anchorPageScreenTop) {
        schedulePageUpdate(() -> {
            if (mDocView == null || pageIndexes == null) return;
            for (int pageIdx : pageIndexes) {
                PageView pv = (PageView) mDocView.getView(pageIdx);
                if (pv != null) pv.update();
            }
            mDocView.restorePagePosition(anchorPageIndex, anchorPageScreenTop);
            Debugger.i("Restored page position: page=" + anchorPageIndex + ",top=" + anchorPageScreenTop);
        });
    }

    private void afterAnnotationPreservingScroll() {
        if (mDocView == null) return;
        int anchorPageIndex = mDocView.findPageAtY(mDocView.getDocumentScrollY());
        int anchorPageScreenTop = mDocView.getPageScreenTop(anchorPageIndex);
        mDocView.afterAnnotation();
        mDocView.restorePagePosition(anchorPageIndex, anchorPageScreenTop);
    }

    private void refreshDocumentAndShowPage(final int pageIndex) {
        if (mDocView == null || core == null) return;
        final int pageCount = core.countPages();
        if (pageCount <= 0) return;
        final int targetPage = Math.max(0, Math.min(pageCount - 1, pageIndex));
        mDocView.afterAnnotation();
        postToMain(() -> {
            if (mDocView == null || core == null) return;
            int latestPageCount = core.countPages();
            if (latestPageCount <= 0) return;
            final int latestTargetPage = Math.max(0, Math.min(latestPageCount - 1, targetPage));
            mDocView.setDisplayedViewIndex(latestTargetPage);
            updatePageNumView(latestTargetPage);
            mDocView.post(() -> refreshDisplayedPageWhenReady(latestTargetPage, 0));
            postToMainDelayed(() -> refreshDisplayedPageWhenReady(latestTargetPage, 0), 180);
            postToMainDelayed(() -> refreshDisplayedPageWhenReady(latestTargetPage, 0), 420);
            postToMainDelayed(() -> refreshDisplayedPageWhenReady(latestTargetPage, 0), 800);
        });
    }

    private void refreshDisplayedPageWhenReady(final int pageIndex, int retryCount) {
        if (mDocView == null) return;
        View view = mDocView.getView(pageIndex);
        if (view instanceof PageView) {
            PageView pageView = (PageView) view;
            if (pageView.getPage() == pageIndex && !pageView.isBlankPage()) {
                pageView.update();
                return;
            }
        }
        if (retryCount < 5) {
            mDocView.setDisplayedViewIndex(pageIndex);
            postToMainDelayed(() -> refreshDisplayedPageWhenReady(pageIndex, retryCount + 1), 120 + retryCount * 80L);
        }
    }
    //</editor-fold>

    public static void jump(Context context, MupdfConfig config) {
        Bundle bundle = new Bundle();
        bundle.putString(MupdfMacro.bundle_key_file_path, config.getFilePath());
        bundle.putString(MupdfMacro.bundle_key_annotation_save_path, config.getAnnotationSaveDirPath());
        bundle.putString(MupdfMacro.bundle_key_file_uri, config.getFileUri());
        bundle.putInt(MupdfMacro.bundle_key_file_mediaId, config.getMediaId());
        bundle.putBoolean(MupdfMacro.bundle_key_watermark_enable, config.isWatermarkEnable());
        bundle.putString(MupdfMacro.bundle_key_watermark_content, config.getWatermarkContent());
        bundle.putInt(MupdfMacro.bundle_key_watermark_color, config.getWatermarkColor());
        bundle.putBoolean(MupdfMacro.bundle_key_upload_enable, config.isUploadEnable());
        bundle.putBoolean(MupdfMacro.bundle_key_annotation_enable, config.isAnnotationEnable());
        bundle.putBoolean(MupdfMacro.bundle_key_signature_enable, config.isSignatureEnable());
        bundle.putBoolean(MupdfMacro.bundle_key_capture_enable, config.isCaptureEnable());
        bundle.putBoolean(MupdfMacro.bundle_key_wps_open_enable, config.isWpsOpenEnable());
        bundle.putInt(MupdfMacro.bundle_key_upload_dirId, config.getUploadDirId());
        bundle.putBoolean(MupdfMacro.bundle_key_delete_file, config.isDeleteSourceFile());
        bundle.putBoolean(MupdfMacro.bundle_key_only_preview, config.isOnlyPreview());
        bundle.putInt(MupdfMacro.bundle_key_page_index, config.getPageIndex());
        bundle.putInt(MupdfMacro.bundle_key_clarityLimitMode, config.getClarityLimitMode());
        bundle.putBoolean(MupdfMacro.bundle_key_full_screen, config.isFullScreenEnable());
        bundle.putInt(MupdfMacro.bundle_key_background_color, config.getBackgroundColor());
        bundle.putBoolean(MupdfMacro.bundle_key_background_color_configured, config.isBackgroundColorConfigured());
        bundle.putInt(MupdfMacro.bundle_key_brightness, config.getBrightness());
        bundle.putBoolean(MupdfMacro.bundle_key_brightness_configured, config.isBrightnessConfigured());
        bundle.putInt(MupdfMacro.bundle_key_zoom_percent, config.getZoomPercent());
        bundle.putBoolean(MupdfMacro.bundle_key_zoom_percent_configured, config.isZoomPercentConfigured());
        bundle.putBoolean(MupdfMacro.bundle_key_window_watermark_enable, config.isWindowWatermarkEnable());
        bundle.putString(MupdfMacro.bundle_key_window_watermark_content, config.getWindowWatermarkContent());
        bundle.putInt(MupdfMacro.bundle_key_window_watermark_color, config.getWindowWatermarkColor());
        bundle.putBoolean(MupdfMacro.bundle_key_signature_form_enabled, config.isSignatureFormEnabled());
        bundle.putBoolean(MupdfMacro.bundle_key_fill_signature_form_enabled, config.isFillInSignatureEnabled());
        bundle.putBoolean(MupdfMacro.bundle_key_annotation_input_text_enabled, config.isAnnotationInputTextEnabled());
        jump(context, bundle);
    }

    public static void jump(Context context, Bundle bundle) {
        ActUtil.finishActivity(MuPdfDocumentActivity.class);
        Intent intent = new Intent(context, MuPdfDocumentActivity.class);
        //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);//使用此标志后，进入画板返回时无法返回当前页面
        intent.setAction(Intent.ACTION_VIEW);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(MupdfMacro.mupdf_bundle_key, bundle);
        Debugger.e(TAG, "打开MuPdfDocumentActivity");
        context.startActivity(intent);
    }

    @Override
    public Resources getResources() {
        Resources superResources = super.getResources();
        // cancelAdapt 必须在主线程调用，但 getResources 可能被 Binder 线程调用
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AutoSizeCompat.cancelAdapt(superResources);
        }
        return superResources;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setNavBarVisibility(getWindow(), false);
        super.onCreate(savedInstanceState);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDisplayDPI = (int) metrics.densityDpi;
        mAlertBuilder = new AlertDialog.Builder(this);
        Debugger.i(TAG, "mupdf version: 6.0.19");
        if (core == null) {
            if (savedInstanceState != null && savedInstanceState.containsKey("DocTitle")) {
                mDocTitle = savedInstanceState.getString("DocTitle");
            }
        }
        if (core == null) {
            Intent intent = getIntent();

            mReturnToLibraryActivity = intent.getIntExtra(getComponentName().getPackageName() + ".ReturnToLibraryActivity", 0) != 0;

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Bundle bundle = intent.getBundleExtra(MupdfMacro.mupdf_bundle_key);
                if (bundle != null) {
                    String mimetype = getIntent().getType();
                    srcFilePath = bundle.getString(MupdfMacro.bundle_key_file_path, "");
                    annotationSavePath = bundle.getString(MupdfMacro.bundle_key_annotation_save_path, "");
                    if (annotationSavePath.isEmpty()) {
                        annotationSavePath = getExternalFilesDir("annotation").getAbsolutePath();
                    }
                    srcUri = bundle.getString(MupdfMacro.bundle_key_file_uri, "");
                    mediaId = bundle.getInt(MupdfMacro.bundle_key_file_mediaId, 0);
                    uploadEnable = bundle.getBoolean(MupdfMacro.bundle_key_upload_enable, true);
                    annotationEnable = bundle.getBoolean(MupdfMacro.bundle_key_annotation_enable, true);
                    signatureEnable = bundle.getBoolean(MupdfMacro.bundle_key_signature_enable, true);
                    captureEnable = bundle.getBoolean(MupdfMacro.bundle_key_capture_enable, true);
                    wpsOpenEnable = bundle.getBoolean(MupdfMacro.bundle_key_wps_open_enable, true);
                    deleteFileWhenExit = bundle.getBoolean(MupdfMacro.bundle_key_delete_file, true);
                    isOnlyPreview = bundle.getBoolean(MupdfMacro.bundle_key_only_preview, false);
                    uploadDirId = bundle.getInt(MupdfMacro.bundle_key_upload_dirId, 2);
                    srcPageIndex = bundle.getInt(MupdfMacro.bundle_key_page_index, 0);
                    MupdfMacro.clarityLimitMode = bundle.getInt(MupdfMacro.bundle_key_clarityLimitMode, -1);
                    isFullScreen = bundle.getBoolean(MupdfMacro.bundle_key_full_screen, true);
                    SharedPreferences displayPrefs = getDisplaySettingsPrefs();
                    boolean backgroundConfigured = bundle.containsKey(MupdfMacro.bundle_key_background_color_configured)
                            ? bundle.getBoolean(MupdfMacro.bundle_key_background_color_configured, false)
                            : bundle.containsKey(MupdfMacro.bundle_key_background_color);
                    boolean brightnessConfigured = bundle.containsKey(MupdfMacro.bundle_key_brightness_configured)
                            ? bundle.getBoolean(MupdfMacro.bundle_key_brightness_configured, false)
                            : bundle.containsKey(MupdfMacro.bundle_key_brightness);
                    boolean zoomConfigured = bundle.containsKey(MupdfMacro.bundle_key_zoom_percent_configured)
                            ? bundle.getBoolean(MupdfMacro.bundle_key_zoom_percent_configured, false)
                            : bundle.containsKey(MupdfMacro.bundle_key_zoom_percent);

                    MupdfMacro.backgroundColor = backgroundConfigured
                            ? bundle.getInt(MupdfMacro.bundle_key_background_color, MupdfMacro.DEFAULT_BACKGROUND_COLOR)
                            : displayPrefs.getInt(PREF_BACKGROUND_COLOR, MupdfMacro.DEFAULT_BACKGROUND_COLOR);
                    MupdfMacro.brightness = MupdfMacro.clampBrightness(brightnessConfigured
                            ? bundle.getInt(MupdfMacro.bundle_key_brightness, 0)
                            : displayPrefs.getInt(PREF_BRIGHTNESS, 0));
                    configuredZoomPercent = zoomConfigured
                            ? bundle.getInt(MupdfMacro.bundle_key_zoom_percent, MupdfMacro.ZOOM_PERCENT_UNSET)
                            : displayPrefs.getInt(PREF_ZOOM_PERCENT, MupdfMacro.ZOOM_PERCENT_UNSET);
                    Uri uri;
                    if (!srcFilePath.isEmpty()) {
                        uri = Uri.parse(new File(srcFilePath).toURI().toString());
                    } else {
                        uri = Uri.parse(srcUri);
                    }
                    if (uri == null) {
                        Debugger.e("srcFilePath can not parse uri");
                        uri = Uri.parse(srcUri);
                    }

                    watermarkEnable = bundle.getBoolean(MupdfMacro.bundle_key_watermark_enable, false);
                    if (watermarkEnable) {
                        mWatermark = bundle.getString(MupdfMacro.bundle_key_watermark_content, "");
                        mWatermarkColor = bundle.getInt(MupdfMacro.bundle_key_watermark_color, Color.parseColor("#66FF6D00"));
                    }
                    mWindowWatermarkEnabled = bundle.getBoolean(MupdfMacro.bundle_key_window_watermark_enable, false);
                    if (mWindowWatermarkEnabled) {
                        mWindowWatermark = bundle.getString(MupdfMacro.bundle_key_window_watermark_content, "");
                        mWindowWatermarkColor = bundle.getInt(MupdfMacro.bundle_key_window_watermark_color, Color.parseColor("#33FFAB00"));
                    }
                    mSignatureFormEnabled = bundle.getBoolean(MupdfMacro.bundle_key_signature_form_enabled, false);
                    mfillSignatureFormEnabled = bundle.getBoolean(MupdfMacro.bundle_key_fill_signature_form_enabled, false);
                    mAnnotationInputTextEnabled = bundle.getBoolean(MupdfMacro.bundle_key_annotation_input_text_enabled, false);
                    Debugger.i(TAG, "bundle config："
                            + "\nsrcFilePath=" + srcFilePath
                            + "\nsrcUri=" + srcUri
                            + "\nuri=" + uri
                            + "\nmediaId=" + mediaId
                            + "\ndeleteFileWhenExit=" + deleteFileWhenExit
                            + "\nisOnlyPreview=" + isOnlyPreview
                            + "\nuploadDirId=" + uploadDirId
                            + "\nwatermarkEnable=" + watermarkEnable
                            + "\nmWatermark=" + mWatermark
                            + "\nmWindowWatermarkEnabled=" + mWindowWatermarkEnabled
                            + "\nmWindowWatermark=" + mWindowWatermark
                            + "\nMupdfMacro.clarityLimitMode=" + MupdfMacro.clarityLimitMode
                            + "\nisFullScreen=" + isFullScreen
                            + "\nmSignatureFormEnabled=" + mSignatureFormEnabled
                            + "\nmfillSignatureFormEnabled=" + mfillSignatureFormEnabled
                            + "\nmAnnotationInputTextEnabled=" + mAnnotationInputTextEnabled
                    );

                    if (uri == null) {
                        showCannotOpenDialog();
                        return;
                    }

                    mDocKey = uri.toString();

                    Debugger.i(TAG, "OPEN filePath " + srcFilePath);
                    Debugger.i(TAG, "OPEN URI " + uri);
                    Debugger.i(TAG, "OPEN mimetype " + mimetype);

                    mDocTitle = null;
                    long size = -1;
                    Cursor cursor = null;

                    try {
                        cursor = getContentResolver().query(uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int idx;

                            idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_STRING)
                                mDocTitle = cursor.getString(idx);

                            idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                            if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_INTEGER)
                                size = cursor.getLong(idx);

                            if (size == 0)
                                size = -1;
                        }
                    } catch (Exception x) {
                        // Ignore any exception and depend on default values for title
                        // and size (unless one was decoded
                    } finally {
                        if (cursor != null)
                            cursor.close();
                    }
                    Debugger.i(TAG, "  NAME " + mDocTitle);
                    Debugger.i(TAG, "  SIZE " + size);

                    if (mimetype == null || mimetype.equals("application/octet-stream")) {
                        mimetype = getContentResolver().getType(uri);
                        Debugger.i(TAG, "  MAGIC (Resolved) " + mimetype);
                    }
                    if (mimetype == null || mimetype.equals("application/octet-stream")) {
                        mimetype = mDocTitle;
                        Debugger.i(TAG, "  MAGIC (Filename) " + mimetype);
                    }
                    if (srcFilePath != null && !srcFilePath.isEmpty()) {
                        mDocTitle = Util.getFileName(srcFilePath);
                        Debugger.i(TAG, "  NAME " + mDocTitle);
                        try {
                            core = openFile(srcFilePath);
                            SearchTaskResult.set(null);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (core == null) {
                        try {
                            core = openCore(uri, size, "application/pdf");
                            SearchTaskResult.set(null);
                        } catch (Exception x) {
                            Debugger.e(x.toString());
                            showCannotOpenDialog();
                            return;
                        }
                    }
                }
            }
            if (core != null && core.needsPassword()) {
                requestPassword(savedInstanceState);
                return;
            }
            if (core != null && core.countPages() == 0) {
                Debugger.e("countPages为0");
                core = null;
            }
        }
        if (core == null) {
            showCannotOpenDialog();
            return;
        }

        applyWindowBrightness();
        createUI(savedInstanceState);
        registerEventBus();
        ActUtil.addActivity(this);
    }

    /**
     * ReaderView 在测量页面时已经按容器宽度做了适宽，mScale=1 即页宽与屏幕宽度对齐。
     */
    float fullWidthScale = 1.0f;
    private static final int ABSOLUTE_MIN_ZOOM_PERCENT = 1;
    private static final int FIT_WIDTH_ZOOM_PERCENT = 100;
    private static final int MAX_ZOOM_PERCENT = FIT_WIDTH_ZOOM_PERCENT;
    /**
     * 当前缩放百分比，100 表示 PDF 页宽与屏幕宽度对齐。
     */
    private int currentZoomPercent = FIT_WIDTH_ZOOM_PERCENT;

    public void createUI(Bundle savedInstanceState) {
        if (core == null)
            return;
        fullWidthScale = 1.0f;
        if (isFullScreen) {
            PointF size = core.getPageSize(0);
            Debugger.i(TAG, "createUI: size=" + size + ",fullWidthScale=" + fullWidthScale);
        }
        mDocView = new ReaderView(this, fullWidthScale) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                currentPageIndex = i;
                mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", i + 1, core.countPages()));

                if (thumbnailAdapter != null) {
                    thumbnailAdapter.setCurrentPage(i);
                    if (rvThumbnails != null) {
                        rvThumbnails.smoothScrollToPosition(i);
                    }
                }
                Debugger.i(TAG, "onMoveToChild: currentPageIndex=" + currentPageIndex);
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
                Debugger.i(TAG, "onTapMainDocArea: ");
                if (!mButtonsVisible) {
                    showButtons();
                } else {
                    if (mTopBarMode == TopBarMode.Main)
                        hideButtons();
                }
            }

            @Override
            protected void onDocMotion() {
                Debugger.i(TAG, "onDocMotion: ");
                // 搜索模式下拖动文档不隐藏工具栏，避免搜索框消失影响连续搜索
                if (mTopBarMode != TopBarMode.Search) {
                    hideButtons();
                }
            }

            @Override
            public void onSizeChanged(int w, int h, int oldw, int oldh) {
                Debugger.i(TAG, "onSizeChanged: size:" + w + "," + h + ", old size:" + oldw + "," + oldh + ",core.isReflowable()=" + core.isReflowable());
                if (core.isReflowable()) {
                    mLayoutW = w * 72 / mDisplayDPI;
                    mLayoutH = h * 72 / mDisplayDPI;
                    relayoutDocument();
                } else {
                    refresh();
                }
            }
        };
        mDocView.setScaleBoundsProvider(new ReaderView.ScaleBoundsProvider() {
            @Override
            public float getMinScale() {
                return fullWidthScale * getFitPageZoomPercent() / 100f;
            }

            @Override
            public float getMaxScale() {
                return fullWidthScale * MAX_ZOOM_PERCENT / 100f;
            }
        });
        PageAdapter pageAdapter = new PageAdapter(this, core, fullWidthScale, "");
        mDocView.setAdapter(pageAdapter);

        mSearchTask = new SearchTask(this, core) {
            @Override
            protected void onTextFound(SearchTaskResult result) {
                SearchTaskResult.set(result);
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };

        initViews();
        extracted(savedInstanceState);
        viewConfig();

        RelativeLayout.LayoutParams docLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        // 插入到最底层（索引0），使顶部栏和底部页码覆盖在文档之上
        rootView.addView(mDocView, 0, docLp);

        applyWindowWatermark(mWindowWatermark);
        setContentView(mButtonsView);
        if (watermarkEnable) {
            float[] color = core.parseColor(mWatermarkColor);
            core.addContentWatermark(mWatermark, 0f, 45f, 0.15f, color, 1.5f);
        }
        postToMainDelayed(() -> {
            if (srcPageIndex != 0) {
                mDocView.setDisplayedViewIndex(srcPageIndex);
            }
            applyZoomPercent(resolveInitialZoomPercent(), false);
            mDocView.requestLayout();
            mDocView.run();


            // 缩略图
            initThumbnailDrawer();
        }, 500L);

        Debugger.i(TAG, "createUI: end");
    }

    private void initViews() {
        int layoutResId = MupdfMacro.isHengXunVersion ? R.layout.mupdf_document_activity_hengxun : R.layout.mupdf_document_activity;
        mButtonsView = getLayoutInflater().inflate(layoutResId, null);
        rootView = (RelativeLayout) mButtonsView.findViewById(R.id.rootView);
        mDocNameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
        mLlPageView = (LinearLayout) mButtonsView.findViewById(R.id.ll_page_view);
        mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);//页码
        mPrePageView = (TextView) mButtonsView.findViewById(R.id.prePage);//上一页
        mNextPageView = (TextView) mButtonsView.findViewById(R.id.nextPage);//下一页
        mLayoutButton = mButtonsView.findViewById(R.id.layoutButton);
        //提交签名、取消签名
        ll_signature_layout = mButtonsView.findViewById(R.id.ll_signature_layout);
        tv_submit_signature = mButtonsView.findViewById(R.id.tv_submit_signature);
        tv_cancel_signature = mButtonsView.findViewById(R.id.tv_cancel_signature);

        //<editor-fold desc="顶部默认组件">
        mTopBarSwitcher = mButtonsView.findViewById(R.id.switcher);
        mSearchBack = mButtonsView.findViewById(R.id.searchBack);
        mSearchFwd = mButtonsView.findViewById(R.id.searchForward);
        mSearchClose = mButtonsView.findViewById(R.id.searchClose);
        mSearchText = mButtonsView.findViewById(R.id.searchText);
        viewTopThumbnail = mButtonsView.findViewById(R.id.viewTopThumbnail);
        viewTopRefresh = mButtonsView.findViewById(R.id.viewTopRefresh);
        viewTopJump = mButtonsView.findViewById(R.id.viewTopJump);
        viewTopScreenshot = mButtonsView.findViewById(R.id.viewTopScreenshot);
        viewTopSignature = mButtonsView.findViewById(R.id.viewTopSignature);
        viewTopAnnotation = mButtonsView.findViewById(R.id.viewTopAnnotation);
        viewTopBookmark = mButtonsView.findViewById(R.id.viewTopBookmark);
        viewTopWatermark = mButtonsView.findViewById(R.id.viewTopWatermark);
        viewTopSignTable = mButtonsView.findViewById(R.id.viewTopSignTable);
        viewTopSignRow = mButtonsView.findViewById(R.id.viewTopSignRow);
        viewTopClose = mButtonsView.findViewById(R.id.viewTopClose);
        viewTopSearch = mButtonsView.findViewById(R.id.viewTopSearch);
        viewTopSetting = mButtonsView.findViewById(R.id.viewTopSetting);
        //</editor-fold>

        //<editor-fold desc="批注控件">
        inkOperationSwitcher = mButtonsView.findViewById(R.id.inkOperationSwitcher);
        //关闭
        viewArtClose = mButtonsView.findViewById(R.id.viewArtClose);
        //画笔粗细
        viewArtSizeTv = mButtonsView.findViewById(R.id.viewArtSizeTv);
        viewArtSeekBar = mButtonsView.findViewById(R.id.viewArtSeekBar);
        //画笔
        viewArtPen = mButtonsView.findViewById(R.id.viewArtPen);
        //直线
        viewArtLine = mButtonsView.findViewById(R.id.viewArtLine);
        //删除
        viewArtBrush = mButtonsView.findViewById(R.id.viewArtBrush);
        //颜色
        viewArtColor = mButtonsView.findViewById(R.id.viewArtColor);
        //共享
        viewArtInvite = mButtonsView.findViewById(R.id.viewArtInvite);
        //高亮
        viewArtHighlight = mButtonsView.findViewById(R.id.viewArtHighlight);
        //撤销
        viewArtRevoke = mButtonsView.findViewById(R.id.viewArtRevoke);
        //下划线
        viewArtUnderline = mButtonsView.findViewById(R.id.viewArtUnderline);
        //删除线
        viewArtStrikeout = mButtonsView.findViewById(R.id.viewArtStrikeout);
        //自由文本
        viewArtFreeText = mButtonsView.findViewById(R.id.viewArtFreeText);
        //确定
        viewArtDone = mButtonsView.findViewById(R.id.viewArtDone);
        //</editor-fold>

        //<editor-fold desc="缩略图控件">
        drawerLayout = (DrawerLayout) mButtonsView.findViewById(R.id.drawer_layout);
        rvThumbnails = (RecyclerView) mButtonsView.findViewById(R.id.rv_thumbnails);
        //</editor-fold>

        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mLlPageView.setVisibility(View.INVISIBLE);
    }

    private void viewConfig() {
        //文件名称
        mDocNameView.setText(mDocTitle);
        setViewShowState(viewTopWatermark, mWindowWatermarkEnabled); //水印
        setViewShowState(viewTopSignTable, mSignatureFormEnabled);//签名表
        setViewShowState(viewTopSignRow, mfillSignatureFormEnabled);//填写签名
        setViewShowState(viewTopSignature, signatureEnable);        //签名
        if (MupdfMacro.shareAnnotationEnable) {
            // 有文件id才显示
            setViewShowState(viewArtInvite, mediaId != 0);   //批注中的邀请
        }
        setViewShowState(viewTopJump, wpsOpenEnable);               //外部打开
        setViewShowState(viewTopScreenshot, captureEnable);         //截图批注
        setViewShowState(viewTopAnnotation, annotationEnable);      //批注
        setViewShowState(viewArtFreeText, mAnnotationInputTextEnabled);      //批注输入文本
        if (isOnlyPreview) {
            setViewShowState(viewTopScreenshot, false);
            setViewShowState(viewTopSignature, false);
            setViewShowState(viewTopAnnotation, false);
            setViewShowState(viewTopWatermark, false);
            setViewShowState(viewTopSignTable, false);
            setViewShowState(viewTopSearch, false);
        }
    }

    private void initThumbnailDrawer() {
        if (rvThumbnails == null) return;
        rvThumbnails.setLayoutManager(new LinearLayoutManager(this));
        thumbnailAdapter = new ThumbnailAdapter(this, core, 128, 128);
        rvThumbnails.setAdapter(thumbnailAdapter);
        thumbnailAdapter.setOnThumbnailClickListener(new ThumbnailAdapter.OnThumbnailClickListener() {
            @Override
            public void onThumbnailClick(int position) {
                if (mDocView != null) {
                    mDocView.setDisplayedViewIndex(position);
                    if (thumbnailAdapter != null) thumbnailAdapter.setCurrentPage(position);
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                }
            }
        });
    }

    long lastClickTime = 0L;

    private void extracted(Bundle savedInstanceState) {

        //<editor-fold desc="文本查找：搜索栏切换与搜索按钮">
        mSearchClose.setOnClickListener(v -> searchModeOff());
        mSearchBack.setEnabled(false);
        mSearchFwd.setEnabled(false);
        mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
        mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));
        mSearchBack.setOnClickListener(v -> search(-1));
        mSearchFwd.setOnClickListener(v -> search(1));
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                boolean haveText = s.toString().length() > 0;
                setButtonEnabled(mSearchBack, haveText);
                setButtonEnabled(mSearchFwd, haveText);
                // Remove any previous search results
                if (SearchTaskResult.get() != null
                        && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
                    SearchTaskResult.set(null);
                    mDocView.resetupChildren();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        mSearchText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                search(1);
            }
            return false;
        });
        mSearchText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                search(1);
            }
            return false;
        });

        // 顶部搜索入口按钮
        if (viewTopSearch != null) {
            viewTopSearch.setOnClickListener(v -> {
                showButtons();
                searchModeOn();
            });
        }

        // 顶部设置入口按钮（亮度、背景颜色、缩放）
        if (viewTopSetting != null) {
            viewTopSetting.setOnClickListener(v -> showSettingsDialog());
        }

        //</editor-fold>

        //退出pdf预览
        viewTopClose.setOnClickListener(v -> {
            exit();
        });
        //刷新，重新加载当前页
        viewTopRefresh.setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastClickTime > 3000) {
                lastClickTime = System.currentTimeMillis();
                mDocView.setDisplayedViewIndex(mDocView.mCurrent);
                core.logAnnotations(mDocView.mCurrent);
            }
        });
        //内容流水印
        viewTopWatermark.setOnClickListener(v -> {
            showWatermarkDialog();
        });
        //签名表格
        if (viewTopSignTable != null) {
            viewTopSignTable.setOnClickListener(v -> {
                showSignTableDialog();
            });
        }
        //填写签名
        if (viewTopSignRow != null) {
            viewTopSignRow.setOnClickListener(v -> {
                showSignRowDialog();
            });
        }

        //<editor-fold desc="签名操作">
        //签名
        viewTopSignature.setOnClickListener(v -> {
            mDocView.savePosition();
            hideButtons();
            new ArtBoardDialog(this, false, new ArtBoardDialog.SignatureListener() {
                @Override
                public void onSuccess(Object[] object) {
                    ll_signature_layout.setVisibility(View.VISIBLE);
                    isSigning = true;
                    mDocView.setSigning(true);
                    mCurPageView = (PageView) mDocView.getDisplayedView();
                    int width = mCurPageView.getWidth();
                    int height = mCurPageView.getHeight();
                    int top = mCurPageView.getTop();
                    Debugger.i(TAG, "onSuccess 开启批注:(" + width + "," + height + ")" + top);

                    List<SignatureBoard.DrawPath> drawPaths = (List<SignatureBoard.DrawPath>) object[0];
                    RectF regionSize = (RectF) object[1];

                    int offset = 50;
                    int scalableViewWidth = (int) (regionSize.right - regionSize.left) + offset * 2;
                    int scalableViewHeight = (int) (regionSize.bottom - regionSize.top) + offset * 2;

                    int l = width / 2 - scalableViewWidth / 2;
                    int t = Math.abs(top) + 100;
                    int r = width / 2 + scalableViewWidth / 2;
                    int b = scalableViewHeight + Math.abs(top) + 100;

                    mScalableView = new ScalableView(MuPdfDocumentActivity.this, drawPaths
                            , regionSize.left, regionSize.top
                            , regionSize.right, regionSize.bottom
                            , l, t, r, b
                            , width, height, offset);

                    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(scalableViewWidth, scalableViewHeight);
                    mScalableView.setLayoutParams(params);
                    mCurPageView.addView(mScalableView);

                    mScalableView.layout(l, t, r, b);
                }
            }).show();
        });
        //提交签名
        tv_submit_signature.setOnClickListener(v -> {
            if (mScalableView == null || mCurPageView == null) return;
            List<SignatureBoard.DrawPath> drawPaths = mScalableView.getDrawPaths();
            PageView pageView = mCurPageView;
            int signaturePageIndex = pageView.getPage();
            int width = pageView.getWidth();
            int height = pageView.getHeight();

            List<MupdfAnnotationBean> annotationBeans = new ArrayList<>();
            for (SignatureBoard.DrawPath drawPath : drawPaths) {
                PointF[] points = drawPath.points;
                Point[] array = new Point[points.length];
                for (int i = 0; i < points.length; i++) {
                    float x = points[i].x;
                    float y = points[i].y;
                    array[i] = new Point(x, y);
                }
                Point[] percentPoints = core.addAnnotation(signaturePageIndex, width, height, PDFAnnotation.TYPE_INK, 5 / 3.0f, drawPath.color, array);
                //points是经过core.addAnnotation方法计算后的实际坐标
                annotationBeans.add(new MupdfAnnotationBean(mediaId, signaturePageIndex + 1, PDFAnnotation.TYPE_INK, 5 / 3.0f, drawPath.color, percentPoints));
                hadAnnotation = true;
            }
            if (MupdfMacro.isSharing && !annotationBeans.isEmpty()) {
                EventBus.getDefault().post(new MupdfEventMessage.Builder()
                        .type(MupdfBusType.inform_share_annotation)
                        .objects(annotationBeans)
                        .build());
            }

            mCurPageView.removeView(mScalableView);
            mScalableView = null;
            mCurPageView = null;
            ll_signature_layout.setVisibility(View.GONE);
            isSigning = false;
            mDocView.setSigning(false);
            pageView.update();
        });
        //取消签名
        tv_cancel_signature.setOnClickListener(v -> {
            if (mCurPageView != null && mScalableView != null) {
                mCurPageView.removeView(mScalableView);
            }
            mScalableView = null;
            mCurPageView = null;
            ll_signature_layout.setVisibility(View.GONE);
            isSigning = false;
            mDocView.setSigning(false);
        });
        //</editor-fold>

        //外部打开
        viewTopJump.setOnClickListener(v -> {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(R.string.open_document_tip);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_ensure), (dialog, which) -> {
                dialog.dismiss();
                deleteFileWhenExit = false;
                finish();
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.out_open_inform).objects(srcFilePath, srcUri).build());
            });
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                    (dialog, which) -> dialog.dismiss());
            alert.setOnCancelListener(dialog -> dialog.dismiss());
            alert.show();
        });
        //截图批注
        viewTopScreenshot.setOnClickListener(v -> {
            hideButtons();
            postToMainDelayed(() -> {
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_screenshot).objects(mDocTitle, 0).build());
            }, 250);
        });
        //界面跳转
        mPageNumberView.setOnClickListener(v -> {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(getString(R.string.jump_to_specific_page));
            EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            alert.setView(editText);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, "确定", (dialog, which) -> {
                String number = editText.getText().toString().trim();
                if (number.isEmpty()) {
                    toast(getString(R.string.please_enter_page_number));
                    return;
                }
                int max = core.countPages();
                int value = Integer.parseInt(number);
                if (value < 1) {
                    toast(getString(R.string.minimum_page_number_is_1));
                    return;
                }
                if (value > max) {
                    toast(getString(R.string.cannot_exceed_the_maximum_page_size));
                    return;
                }
                mDocView.setDisplayedViewIndex(value - 1);
                dialog.dismiss();
            });
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
            alert.setOnCancelListener(dialog -> dialog.dismiss());
            alert.show();
        });
        //上一页
        mPrePageView.setOnClickListener(v -> {
            Debugger.i(TAG, "页码跳转 上一页:" + currentPageIndex);
            if (currentPageIndex > 0) {
                mDocView.setDisplayedViewIndex(currentPageIndex - 1);
            }
        });
        //下一页
        mNextPageView.setOnClickListener(v -> {
            int countPages = core.countPages();
            Debugger.i(TAG, "页码跳转 下一页:" + currentPageIndex + ",countPages=" + countPages);
            if (currentPageIndex <= countPages) {
                mDocView.setDisplayedViewIndex(currentPageIndex + 1);
            }
        });

        //缩略图
        if (viewTopThumbnail != null) {
            viewTopThumbnail.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            });
        }

        if (core.isReflowable()) {
            mLayoutButton.setVisibility(View.VISIBLE);
            mLayoutPopupMenu = new PopupMenu(this, mLayoutButton);
            mLayoutPopupMenu.getMenuInflater().inflate(R.menu.layout_menu, mLayoutPopupMenu.getMenu());
            mLayoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    float oldLayoutEM = mLayoutEM;
                    int id = item.getItemId();
                    if (id == R.id.action_layout_6pt) mLayoutEM = 6;
                    else if (id == R.id.action_layout_7pt) mLayoutEM = 7;
                    else if (id == R.id.action_layout_8pt) mLayoutEM = 8;
                    else if (id == R.id.action_layout_9pt) mLayoutEM = 9;
                    else if (id == R.id.action_layout_10pt) mLayoutEM = 10;
                    else if (id == R.id.action_layout_11pt) mLayoutEM = 11;
                    else if (id == R.id.action_layout_12pt) mLayoutEM = 12;
                    else if (id == R.id.action_layout_13pt) mLayoutEM = 13;
                    else if (id == R.id.action_layout_14pt) mLayoutEM = 14;
                    else if (id == R.id.action_layout_15pt) mLayoutEM = 15;
                    else if (id == R.id.action_layout_16pt) mLayoutEM = 16;
                    if (oldLayoutEM != mLayoutEM)
                        relayoutDocument();
                    return true;
                }
            });
            mLayoutButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mLayoutPopupMenu.show();
                }
            });
        }

        if (core.hasOutline()) {
            viewTopBookmark.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mFlatOutline == null)
                        mFlatOutline = core.getOutline();
                    if (mFlatOutline != null) {
                        Intent intent = new Intent(MuPdfDocumentActivity.this, OutlineActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putInt("POSITION", mDocView.getDisplayedViewIndex());
                        bundle.putSerializable("OUTLINE", mFlatOutline);
                        intent.putExtra("PALLETBUNDLE", Pallet.sendBundle(bundle));
                        startActivityForResult(intent, OUTLINE_REQUEST);
                    }
                }
            });
        } else {
            viewTopBookmark.setVisibility(View.GONE);
        }

        //<editor-fold desc="批注">
        //开启批注
        viewTopAnnotation.setOnClickListener(v -> {
            mDocView.savePosition();
            hideButtons();
            showAnnotationViews();
            if (artBoard != null) {
                rootView.removeView(artBoard);
                artBoard.recycleBitmapOnly();
                artBoard = null;
            }
            // 画板固定在屏幕位置，坐标通过 documentScrollY 实时转为文档空间
            int artW = mDocView.getWidth();
            int artH = mDocView.getHeight();
            chooseType(1);
            hadAnnotationBeforeCurrentSession = hadAnnotation;
            annotationSessionStartIndex = savedAnnotationPages.size();
            artBoard = new AnnotationArtBoard(this, core, mDocView, artW, artH, new AnnotationArtBoard.DrawExitListener() {
                @Override
                public void onDrawAnnotations(List<AnnotationBean> inkAnnotations) {
                    // 即时保存模式下笔画已逐个提交，这里仅做退出后的刷新
                    Debugger.i(TAG, "onDrawAnnotations 退出批注，hadAnnotation=" + hadAnnotation);
                    if (hadAnnotation) {
                        afterAnnotationPreservingScroll();
                    }
                }
            });
            artBoard.setDocumentScrollY(mDocView.getDocumentScrollY());
            artBoard.setPaintWidth(default_ink_size);
            // 即时保存：每笔松开即提交到 PDF，避免滚动时标注视觉偏移
            artBoard.setStrokeListener(bean -> {
                List<Integer> changedPages = addStrokeAnnotation(bean, artH);
                if (changedPages.isEmpty()) return;
                savedAnnotationPages.add(changedPages);  // 记录用于撤销
                hadAnnotation = true;
                scheduleAnnotationPagesUpdate(changedPages);
            });
            artBoard.setFreeTextListener(pos -> {
                showFreeTextDialog(pos);
            });
            artBoard.setTextMarkupListener((type, start, end, color, strokeWidth) -> {
                if (core == null) return;
                // 点已在文档空间
                int pageIdx = mDocView.findPageAtY((int) start.y);
                int pageTop = mDocView.getPageDocTop(pageIdx);
                int pageLeft = mDocView.getPageScreenLeft(pageIdx);
                int pageW = getAnnotationPageWidth(pageIdx);
                int pageH = mDocView.getPageDisplayHeight(pageIdx);
                if (pageH <= 0) pageH = artH;
                Point localStart = new Point(start.x - pageLeft, start.y - pageTop);
                Point localEnd = new Point(end.x - pageLeft, end.y - pageTop);
                float[] c = core.parseColor(color);
                PDFAnnotation added = core.addTextMarkupAnnotation(pageIdx, pageW, pageH,
                        type, localStart, localEnd, c, mDisplayDPI, mDisplayDPI);
                if (added == null) return;
                hadAnnotation = true;
                // 下划线/删除线/高亮同样计入文档修改：记录变更页，供保存判断、撤销与取消批注使用
                List<Integer> pages = new ArrayList<>();
                pages.add(pageIdx);
                savedAnnotationPages.add(pages);
                PageView pv = (PageView) mDocView.getView(pageIdx);
                if (pv != null) schedulePageUpdate(() -> pv.update());
            });
            rootView.addView(artBoard, 1); // index=1：在 mDocView(0) 之上，mButtonsView(2) 之下
            artBoard.layout(0, 0, artW, artH);
        });

        ibs.add(viewArtBrush);//删除
        ibs.add(viewArtPen);//墨迹
        ibs.add(viewArtLine);//直线
        ibs.add(viewArtHighlight);//高亮，矩形
        ibs.add(viewArtUnderline);//文字下划线
        ibs.add(viewArtStrikeout);//文字删除线
        ibs.add(viewArtFreeText);//自由文本标注
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewArtSeekBar.setMin(1);
        }
        viewArtSeekBar.setMax(100);
        viewArtSizeTv.setText(String.valueOf(default_ink_size));
        viewArtSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = Math.max(progress, 1);
                viewArtSizeTv.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                progress = Math.max(progress, 1);
                viewArtSeekBar.setProgress(progress);
                artBoard.setPaintWidth(progress);
                viewArtSizeTv.setText(String.valueOf(progress));
            }
        });
        //颜色
        viewArtColor.setOnClickListener(v -> {
            new MupdfColorPickerDialog(this, new MupdfColorPickerView.OnColorSubmitListener() {
                @Override
                public void submitColor(int color) {
                    artBoard.setPaintColor(color);
                }
            }, Color.RED).show();
        });
        //撤销
        viewArtRevoke.setOnClickListener(v -> {
            // 即时保存模式：从 PDF 内容层删除最后一笔标注
            if (!savedAnnotationPages.isEmpty()) {
                List<Integer> lastPages = savedAnnotationPages.remove(savedAnnotationPages.size() - 1);
                for (int i = lastPages.size() - 1; i >= 0; i--) {
                    core.deleteLastAnnotation(lastPages.get(i));
                }
                scheduleAnnotationPagesUpdate(lastPages);
            }
            // 同时清理画板残留（橡皮擦后可能遗留）
            if (artBoard != null) {
                artBoard.revoke();
            }
        });
        //删除
        viewArtBrush.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_ERASER) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_ERASER);
                    chooseType(0);
                }
            }
        });
        //画笔
        viewArtPen.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_SLINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_SLINE);
                    chooseType(1);
                }
            }
        });
        //直线
        viewArtLine.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_LINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_LINE);
                    chooseType(2);
                }
            }
        });
        //高亮
        viewArtHighlight.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_RECT) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_RECT);
                    chooseType(3);
                }
            }
        });
        //文字下划线
        viewArtUnderline.setOnClickListener(v -> {
            if (artBoard != null) {
                artBoard.setDrawType(AnnotationArtBoard.DRAW_UNDERLINE);
                chooseType(4);
            }
        });
        //文字删除线
        viewArtStrikeout.setOnClickListener(v -> {
            if (artBoard != null) {
                artBoard.setDrawType(AnnotationArtBoard.DRAW_STRIKEOUT);
                chooseType(5);
            }
        });
        //自由文本标注
        viewArtFreeText.setOnClickListener(v -> {
            if (artBoard != null) {
                artBoard.setDrawType(AnnotationArtBoard.DRAW_FREETEXT);
                chooseType(6);
            }
        });
        //邀请多人批注
        viewArtInvite.setOnClickListener(v -> {
            Debugger.e("邀请多人批注");
            EventBus.getDefault().post(new MupdfEventMessage.Builder()
                    .type(MupdfBusType.inform_invite_annotation)
                    .objects(mDocTitle, mediaId, currentPageIndex + 1)
                    .build());
        });
        //提交批注
        viewArtDone.setOnClickListener(v -> {
            finishAnnotationWithoutRefresh();
        });
        //取消批注
        viewArtClose.setOnClickListener(v -> {
            cancelAnnotationAndHide();
        });
        //</editor-fold>

        if (core.isReflowable()) {
            mLayoutButton.setVisibility(View.VISIBLE);
            mLayoutPopupMenu = new PopupMenu(this, mLayoutButton);
            mLayoutPopupMenu.getMenuInflater().inflate(R.menu.layout_menu, mLayoutPopupMenu.getMenu());
            mLayoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    float oldLayoutEM = mLayoutEM;
                    int id = item.getItemId();
                    if (id == R.id.action_layout_6pt) mLayoutEM = 6;
                    else if (id == R.id.action_layout_7pt) mLayoutEM = 7;
                    else if (id == R.id.action_layout_8pt) mLayoutEM = 8;
                    else if (id == R.id.action_layout_9pt) mLayoutEM = 9;
                    else if (id == R.id.action_layout_10pt) mLayoutEM = 10;
                    else if (id == R.id.action_layout_11pt) mLayoutEM = 11;
                    else if (id == R.id.action_layout_12pt) mLayoutEM = 12;
                    else if (id == R.id.action_layout_13pt) mLayoutEM = 13;
                    else if (id == R.id.action_layout_14pt) mLayoutEM = 14;
                    else if (id == R.id.action_layout_15pt) mLayoutEM = 15;
                    else if (id == R.id.action_layout_16pt) mLayoutEM = 16;
                    if (oldLayoutEM != mLayoutEM)
                        relayoutDocument();
                    return true;
                }
            });
            mLayoutButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mLayoutPopupMenu.show();
                }
            });
        }

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(prefs.getInt("page" + mDocKey, 0));

        if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false)) {
            showButtons();
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false)) {
            searchModeOn();
        }
    }

    private void registerEventBus() {
        if (!EventBus.getDefault().isRegistered(this)) {
            Debugger.e("registerEventBus");
            EventBus.getDefault().register(this);
        }
    }

    private void unregisterEventBus() {
        if (EventBus.getDefault().isRegistered(this)) {
            Debugger.e("unregisterEventBus");
            EventBus.getDefault().unregister(this);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void eventBus(MupdfEventMessage msg) {
        switch (msg.getType()) {
            case MupdfBusType.receive_invite_annotation: {
                Debugger.e("接收到其他人加入的通知");
                break;
            }
            case MupdfBusType.receive_reject_annotation: {
                Debugger.e("接收到其他人拒绝的通知");
                break;
            }
            case MupdfBusType.receive_exit_annotation: {
                Debugger.e("接收到其他人退出的通知");
                break;
            }
            case MupdfBusType.receive_annotation_info: {
                Object[] objects = msg.getObjects();
                Debugger.e("收到其他人的绘制信息 objects数量：" + objects.length);
                boolean onThisPage = false;
                List<MupdfInkBean> inkList = (List<MupdfInkBean>) objects[0];
                for (MupdfInkBean bean : inkList) {
                    int pageNumber = bean.getPageNumber();
                    int linesize = bean.getLinesize();
                    int argb = bean.getArgb();
                    Point[] array = bean.getArray();
                    if (pageNumber == currentPageIndex + 1) onThisPage = true;
                    core.addShareInk(pageNumber, linesize, argb, array);
                }
                if (onThisPage) {
                    Debugger.e("收到其他人的绘制信息 当前页有更新，则进行刷新");
                    if (mAnnotationVisible) {
                        Debugger.e("收到其他人的绘制信息 当前页正在批注，退出批注后再自动刷新");
                        afterAnnotationRefresh = true;
                    } else {
                        afterAnnotationPreservingScroll();
                    }
                }
                break;
            }
        }
    }

    private String toHex(byte[] digest) {
        StringBuilder builder = new StringBuilder(2 * digest.length);
        for (byte b : digest)
            builder.append(String.format("%02x", b));
        return builder.toString();
    }

    private MuPDFCore openBuffer(byte buffer[], String magic) {
        try {
            core = new MuPDFCore(buffer, magic);
        } catch (Exception e) {
            Debugger.e(TAG, "Error opening document buffer: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Debugger.e(TAG, "文件不存在");
            return null;
        }
        Debugger.i(TAG, "Opening File " + filePath);
        try {
            core = new MuPDFCore(filePath);
        } catch (Exception e) {
            Debugger.e(TAG, "Error opening document file: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openStream(SeekableInputStream stm, String magic) {
        try {
            core = new MuPDFCore(stm, magic);
        } catch (Exception e) {
            Debugger.e(TAG, "Error opening document stream: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openCore(Uri uri, long size, String mimetype) throws IOException {
        ContentResolver cr = getContentResolver();

        Debugger.i(TAG, "Opening document " + uri + ",mimetype=" + mimetype);

        InputStream is = cr.openInputStream(uri);
        byte[] buf = null;
        int used = -1;
        try {
            final int limit = 8 * 1024 * 1024;
            if (size < 0) { // size is unknown
                buf = new byte[limit];
                used = is.read(buf);
                boolean atEOF = is.read() == -1;
                if (used < 0 || (used == limit && !atEOF)) // no or partial data
                    buf = null;
            } else if (size <= limit) { // size is known and below limit
                buf = new byte[(int) size];
                used = is.read(buf);
                if (used < 0 || used < size) // no or partial data
                    buf = null;
            }
            if (buf != null && buf.length != used) {
                byte[] newbuf = new byte[used];
                System.arraycopy(buf, 0, newbuf, 0, used);
                buf = newbuf;
            }
        } catch (OutOfMemoryError e) {
            buf = null;
        } finally {
            is.close();
        }

        if (buf != null) {
            Debugger.i(TAG, "  Opening document from memory buffer of size " + buf.length);
            return openBuffer(buf, mimetype);
        } else {
            Debugger.i(TAG, "  Opening document from stream");
            return openStream(new ContentInputStream(cr, uri, size), mimetype);
        }
    }

    private void showCannotOpenDialog() {
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.open_document_fail);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.determine), (dialog, which) -> {
            deleteFileWhenExit = false;
            dialog.dismiss();
            finish();
            EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.out_open_inform).objects(srcFilePath, srcUri).build());
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.exit_review), (dialog, which) -> {
            deleteFileWhenExit = true;
            dialog.dismiss();
            finish();
        });
        alert.setOnCancelListener(dialog -> {
            deleteFileWhenExit = true;
            finish();
        });
        alert.show();
    }

    /**
     * Set the navigation bar's visibility.
     *
     * @param window    The window.
     * @param isVisible True to set navigation bar visible, false otherwise.
     */
    public void setNavBarVisibility(@NonNull final Window window, boolean isVisible) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
        final ViewGroup decorView = (ViewGroup) window.getDecorView();
        for (int i = 0, count = decorView.getChildCount(); i < count; i++) {
            final View child = decorView.getChildAt(i);
            final int id = child.getId();
            if (id != View.NO_ID) {
                String resourceEntryName = getResources().getResourceEntryName(id);
                if ("navigationBarBackground".equals(resourceEntryName)) {
                    child.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
                }
            }
        }
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (isVisible) {
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~uiOptions);
        } else {
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | uiOptions);
        }
    }

    public void requestPassword(final Bundle savedInstanceState) {
        mPasswordView = new EditText(this);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(mPasswordView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay), (dialog, which) -> {
            if (core.authenticatePassword(mPasswordView.getText().toString())) {
                createUI(savedInstanceState);
            } else {
                requestPassword(savedInstanceState);
            }
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                (dialog, which) -> finish());
        alert.show();
    }

    public void relayoutDocument() {
        int loc = core.layout(mDocView.mCurrent, mLayoutW, mLayoutH, mLayoutEM);
        mFlatOutline = null;
        mDocView.mHistory.clear();
        mDocView.refresh();
        mDocView.setDisplayedViewIndex(loc);
    }

    private final List<View> ibs = new ArrayList<>();

    private void chooseType(int index) {
        for (int i = 0; i < ibs.size(); i++) {
            boolean selected = index == i;
            View imageButton = ibs.get(i);
            if (selected) {
                if (imageButton instanceof ImageButton) {
                    ((ImageButton) imageButton).getDrawable().setTint(Color.argb(255, 33, 150, 243));
                } else {
                    imageButton.setSelected(true);
                }
            } else {
                if (imageButton instanceof ImageButton) {
                    ((ImageButton) imageButton).getDrawable().setTint(Color.argb(255, 255, 255, 255));
                } else {
                    imageButton.setSelected(false);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= RESULT_FIRST_USER && mDocView != null) {
//                    mDocView.pushHistory();//不注释的话，用户调用onBackPressed 会关闭当前页加载之前的页面
                    mDocView.setDisplayedViewIndex(resultCode - RESULT_FIRST_USER);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mDocKey != null && mDocView != null) {
            if (mDocTitle != null)
                outState.putString("DocTitle", mDocTitle);

            // Store current page in the prefs against the file name,
            // so that we can pick it up each time the file is loaded
            // Other info is needed only for screen-orientation change,
            // so it can go in the bundle
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mDocKey, mDocView.getDisplayedViewIndex());
            edit.apply();
        }

        if (!mButtonsVisible)
            outState.putBoolean("ButtonsHidden", true);

        if (mTopBarMode == TopBarMode.Search)
            outState.putBoolean("SearchMode", true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSearchTask != null)
            mSearchTask.stop();

        if (mDocKey != null && mDocView != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mDocKey, mDocView.getDisplayedViewIndex());
            edit.apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Debugger.i("onResume inkAnnotations:" + inkAnnotations.size());
        //退出批注画板时，如果批注过则inkAnnotations就不为空
        if (!inkAnnotations.isEmpty() && core != null && mDocView != null) {
            final int anchorPageIndex = mDocView.findPageAtY(mDocView.getDocumentScrollY());
            final int anchorPageScreenTop = mDocView.getPageScreenTop(anchorPageIndex);
            Debugger.i("onResume before annotation: page=" + anchorPageIndex + ",top=" + anchorPageScreenTop);
            View displayedView = mDocView.getDisplayedView();
            int fallbackHeight = displayedView != null ? displayedView.getHeight() : mDocView.getHeight();
            List<Integer> changedPages = new ArrayList<>();
            for (AnnotationBean inkAnnotation : inkAnnotations) {
                for (int pageIdx : addStrokeAnnotation(inkAnnotation, fallbackHeight)) {
                    if (!changedPages.contains(pageIdx)) changedPages.add(pageIdx);
                }
            }
            //绘制到pdf文件后清空
            inkAnnotations.clear();
            scheduleAnnotationPagesUpdateWithRestore(changedPages, anchorPageIndex, anchorPageScreenTop);
        }
    }

    private void toast(String msg) {
        try {
            postToMain(() -> {
                Context applicationContext = getApplicationContext();
                Debugger.e("当前正在签名中 applicationContext=" + applicationContext);
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Debugger.e(TAG, e);
            e.printStackTrace();
        }
    }

    private void showButtons() {
        if (isSigning) {
            Debugger.e("当前正在签名中");
            return;
        }
        if (core == null)
            return;
        if (!mButtonsVisible) {
            mButtonsVisible = true;
            // Update page number text and slider
            int index = mDocView.getDisplayedViewIndex();
            updatePageNumView(index);
            if (mTopBarMode == TopBarMode.Search) {
                mSearchText.requestFocus();
                showKeyboard();
            }

            Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.VISIBLE);
                    mLlPageView.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            mTopBarSwitcher.startAnimation(anim);
        }
    }

    private void hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false;

            Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.INVISIBLE);
                    mLlPageView.setVisibility(View.INVISIBLE);
                }
            });
            mTopBarSwitcher.startAnimation(anim);
        }
    }

    private void searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search;
            mSearchText.requestFocus();
            showKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        }
    }

    private void searchModeOff() {
        if (mTopBarMode == TopBarMode.Search) {
            mTopBarMode = TopBarMode.Main;
            hideKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
            SearchTaskResult.set(null);
            mDocView.resetupChildren();
        }
    }

    private void search(int direction) {
        hideKeyboard();
        int displayPage = mDocView.getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        mSearchTask.go(mSearchText.getText().toString(), direction, displayPage, searchPage);
    }

    private void setButtonEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setColorFilter(enabled ? Color.argb(255, 255, 255, 255) : Color.argb(255, 128, 128, 128));
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(mSearchText, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && mSearchText != null)
            imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
    }

    private void showAnnotationViews() {
        if (!mAnnotationVisible) {
            //签名开始，关闭缩略图面板
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            mAnnotationVisible = true;
            Animation anim = new TranslateAnimation(0, 0, -inkOperationSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(true);
                    inkOperationSwitcher.setVisibility(View.VISIBLE);
//                    mInkLayout.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            inkOperationSwitcher.startAnimation(anim);
        }
    }

    private void finishAnnotationWithoutRefresh() {
        hideAnnotationViews(false, false);
    }

    private void cancelAnnotationAndHide() {
        if (artBoard != null) {
            artBoard.setCancelAnnotation();
        }

        List<Integer> changedPages = new ArrayList<>();
        int startIndex = Math.max(0, Math.min(annotationSessionStartIndex, savedAnnotationPages.size()));
        for (int i = savedAnnotationPages.size() - 1; i >= startIndex; i--) {
            List<Integer> pages = savedAnnotationPages.get(i);
            for (int j = pages.size() - 1; j >= 0; j--) {
                int pageIdx = pages.get(j);
                core.deleteLastAnnotation(pageIdx);
                if (!changedPages.contains(pageIdx)) {
                    changedPages.add(pageIdx);
                }
            }
            savedAnnotationPages.remove(i);
        }
        annotationSessionStartIndex = savedAnnotationPages.size();
        hadAnnotation = hadAnnotationBeforeCurrentSession;
        if (!changedPages.isEmpty()) {
            scheduleAnnotationPagesUpdate(changedPages);
        }
        hideAnnotationViews(true, false);
    }

    private void hideAnnotationViews() {
        hideAnnotationViews(true, true);
    }

    private void hideAnnotationViews(boolean releaseArtBoard, boolean runDeferredRefresh) {
        if (mAnnotationVisible) {
            mAnnotationVisible = false;
            Animation anim = new TranslateAnimation(0, 0, 0, -inkOperationSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(false);
                    inkOperationSwitcher.setVisibility(View.INVISIBLE);
                    viewArtSeekBar.setProgress(default_ink_size);
                    if (artBoard != null) {
                        rootView.removeView(artBoard);
                        if (releaseArtBoard) {
                            artBoard.clear();
                            artBoard.release();
                        } else {
                            artBoard.recycleBitmapOnly();
                        }
                        artBoard = null;
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    if (runDeferredRefresh && afterAnnotationRefresh) {
                        Debugger.e("批注期间有收到别人的共享批注，现在进行刷新");
                        afterAnnotationRefresh = false;
                        afterAnnotationPreservingScroll();
                        //mDocView.setDisplayedViewIndex(mDocView.mCurrent);
                    } else if (!runDeferredRefresh) {
                        afterAnnotationRefresh = false;
                    }
                }
            });
            inkOperationSwitcher.startAnimation(anim);
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        currentPageIndex = index;
        if (mPageNumberView != null)
            mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", index + 1, core.countPages()));
    }


    @Override
    public boolean onSearchRequested() {
        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
            hideButtons();
        } else {
            showButtons();
            searchModeOn();
        }
        return super.onSearchRequested();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//        if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
//            hideButtons();
//        } else {
//            showButtons();
//            searchModeOff();
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        exit();
    }

    private void exit() {
        Debugger.i(TAG, "---exit---");
        if (hadAnnotation) {
            tipSavePop();
        } else {
            finish();
        }
    }

    private void tipSavePop() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mupdf_tip))
                .setMessage(getString(R.string.title_save_pop))
                .setNeutralButton(getString(R.string.mupdf_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(getString(R.string.mupdf_save), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        saveAndExit();
                    }
                })
                .setNegativeButton(getString(R.string.mupdf_not_save), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .create();
        dialog.show();
        // 1. 修改三个按钮的字体大小（以 Positive 为例，其他同理）
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(22);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.mupdf_text_black));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(22);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.mupdf_text_black));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(22);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ContextCompat.getColor(this, R.color.mupdf_text_black));

        // 2. 通过文字内容修改标题大小（不管 ID 是什么）
        setTextViewSizeByText(dialog, getString(R.string.mupdf_tip), 28);

        // 3. 修改消息内容字体（ID 是 android.R.id.message）
        TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setTextSize(25);
            msgView.setTextColor(ContextCompat.getColor(this, R.color.mupdf_text_gray));
        }
        // ---- 新增：修改按钮间距（例如设置为 16dp） ----
        setButtonSpacing(dialog, 20);
    }

    /**
     * 设置对话框按钮之间的间距（适用于原生 AlertDialog）
     *
     * @param dialog    对话框实例
     * @param spacingDp 间距大小（单位 dp）
     */
    private void setButtonSpacing(AlertDialog dialog, int spacingDp) {
        if (dialog == null) return;

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
//        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        // 工具：dp 转 px
        float density = getResources().getDisplayMetrics().density;
        int spacingPx = (int) (spacingDp * density + 0.5f);

        // 依次处理每个按钮的左右边距
        // 注意：原生 AlertDialog 的按钮排列顺序通常是 [Negative] [Neutral] [Positive]
        if (positive != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) positive.getLayoutParams();
            params.leftMargin = spacingPx;   // 左边距拉开
            params.rightMargin = 0;
            positive.setLayoutParams(params);
        }

        if (negative != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) negative.getLayoutParams();
            params.leftMargin = 0;
            params.rightMargin = spacingPx;  // 右边距拉开
            negative.setLayoutParams(params);
        }

        // 如果有中性按钮（它在正负之间），两边都留间距
//        if (neutral != null) {
//            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) neutral.getLayoutParams();
//            params.leftMargin = spacingPx;
//            params.rightMargin = spacingPx;
//            neutral.setLayoutParams(params);
//        }
    }


    /**
     * 在对话框的视图树中，根据文字内容查找 TextView 并修改字体大小
     *
     * @param dialog     对话框
     * @param targetText 要匹配的文字（标题文字）
     * @param size       目标字体大小（sp）
     */
    private void setTextViewSizeByText(AlertDialog dialog, String targetText, float size) {
        if (dialog == null || dialog.getWindow() == null) return;
        View root = dialog.getWindow().getDecorView();
        findAndSetTextSize(root, targetText, size);
    }

    private void findAndSetTextSize(View view, String targetText, float size) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (targetText.equals(tv.getText().toString())) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAndSetTextSize(group.getChildAt(i), targetText, size);
            }
        }
    }

    private void saveAndExit() {
        ProgressDialog progressDialog = new ProgressDialog(MuPdfDocumentActivity.this);
        progressDialog.setMessage(getString(R.string.please_wait));
        progressDialog.setCancelable(false);
        progressDialog.show();
        new Thread(() -> {
            try {
                long l = System.currentTimeMillis();
                String savePath = core.save(srcFilePath, annotationSavePath);
                Debugger.i(TAG, "保存用时：" + (System.currentTimeMillis() - l) + ",savePath=" + savePath);
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_upload).objects(savePath, uploadDirId).build());
                postToMainDelayed(() -> {
                    Debugger.i(TAG, "hideLoading ---progressDialog---");
                    progressDialog.dismiss();
                    MuPdfDocumentActivity.this.finish();
                }, 2000);
            } catch (Exception e) {
                Debugger.e(TAG, e.toString());
                e.printStackTrace();
            }
        }).start();
    }

    private List<Integer> addStrokeAnnotation(AnnotationBean bean, int fallbackHeight) {
        List<Integer> changedPages = new ArrayList<>();
        if (bean == null || core == null || mDocView == null) return changedPages;
        Point[] docPts = bean.getPoints();
        if (docPts == null || docPts.length == 0) return changedPages;

        int type = bean.getType();
        float paintSize = bean.getPaintSize() / 3.0f;
        int paintColor = bean.getPaintColor();
        if (type == PDFAnnotation.TYPE_LINE && docPts.length >= 2) {
            addLineAnnotationByPages(docPts[0], docPts[1], paintSize, paintColor, fallbackHeight, changedPages);
        } else if (type == PDFAnnotation.TYPE_INK) {
            addInkAnnotationByPages(docPts, paintSize, paintColor, fallbackHeight, changedPages);
        } else {
            addSinglePageAnnotation(type, docPts, paintSize, paintColor, fallbackHeight, changedPages);
        }
        return changedPages;
    }

    private void addSinglePageAnnotation(int type, Point[] docPts, float paintSize, int paintColor,
                                         int fallbackHeight, List<Integer> changedPages) {
        int pageIdx = mDocView.findPageAtY((int) docPts[0].y);
        Point[] localPts = new Point[docPts.length];
        for (int i = 0; i < docPts.length; i++) {
            localPts[i] = toPageLocalPoint(docPts[i], pageIdx);
        }
        addAnnotationOnPage(pageIdx, type, localPts, paintSize, paintColor, fallbackHeight, changedPages);
    }

    private void addLineAnnotationByPages(Point start, Point end, float paintSize, int paintColor,
                                          int fallbackHeight, List<Integer> changedPages) {
        int count = core.countPages();
        float dy = end.y - start.y;
        for (int pageIdx = 0; pageIdx < count; pageIdx++) {
            int pageTop = mDocView.getPageDocTop(pageIdx);
            int pageBottom = pageTop + getAnnotationPageHeight(pageIdx, fallbackHeight);
            float t0;
            float t1;
            if (Math.abs(dy) < 0.001f) {
                if (start.y < pageTop || start.y > pageBottom) continue;
                t0 = 0f;
                t1 = 1f;
            } else {
                float topT = (pageTop - start.y) / dy;
                float bottomT = (pageBottom - start.y) / dy;
                t0 = Math.max(0f, Math.min(topT, bottomT));
                t1 = Math.min(1f, Math.max(topT, bottomT));
            }
            if (t1 <= t0) continue;
            Point localStart = toPageLocalPoint(interpolate(start, end, t0), pageIdx);
            Point localEnd = toPageLocalPoint(interpolate(start, end, t1), pageIdx);
            addAnnotationOnPage(pageIdx, PDFAnnotation.TYPE_LINE, new Point[]{localStart, localEnd},
                    paintSize, paintColor, fallbackHeight, changedPages);
        }
    }

    private void addInkAnnotationByPages(Point[] docPts, float paintSize, int paintColor,
                                         int fallbackHeight, List<Integer> changedPages) {
        if (docPts.length == 1) {
            addSinglePageAnnotation(PDFAnnotation.TYPE_INK, docPts, paintSize, paintColor, fallbackHeight, changedPages);
            return;
        }

        List<PagePointSegment> segments = splitInkByPages(docPts, fallbackHeight);
        for (PagePointSegment segment : segments) {
            if (segment.points.size() < 2) continue;
            Point[] localPts = new Point[segment.points.size()];
            for (int i = 0; i < segment.points.size(); i++) {
                localPts[i] = toPageLocalPoint(segment.points.get(i), segment.pageIdx);
            }
            addAnnotationOnPage(segment.pageIdx, PDFAnnotation.TYPE_INK, localPts,
                    paintSize, paintColor, fallbackHeight, changedPages);
        }
    }

    private List<PagePointSegment> splitInkByPages(Point[] docPts, int fallbackHeight) {
        List<PagePointSegment> segments = new ArrayList<>();
        PagePointSegment current = null;
        for (int i = 1; i < docPts.length; i++) {
            List<PageLinePart> parts = clipLineToPages(docPts[i - 1], docPts[i], fallbackHeight);
            for (PageLinePart part : parts) {
                if (current == null || current.pageIdx != part.pageIdx
                        || !samePoint(current.points.get(current.points.size() - 1), part.start)) {
                    if (current != null && current.points.size() >= 2) {
                        segments.add(current);
                    }
                    current = new PagePointSegment(part.pageIdx);
                    current.points.add(part.start);
                }
                if (!samePoint(current.points.get(current.points.size() - 1), part.end)) {
                    current.points.add(part.end);
                }
            }
        }
        if (current != null && current.points.size() >= 2) {
            segments.add(current);
        }
        return segments;
    }

    private List<PageLinePart> clipLineToPages(Point start, Point end, int fallbackHeight) {
        List<PageLinePart> parts = new ArrayList<>();
        int count = core.countPages();
        float dy = end.y - start.y;
        for (int pageIdx = 0; pageIdx < count; pageIdx++) {
            int pageTop = mDocView.getPageDocTop(pageIdx);
            int pageBottom = pageTop + getAnnotationPageHeight(pageIdx, fallbackHeight);
            float t0;
            float t1;
            if (Math.abs(dy) < 0.001f) {
                if (start.y < pageTop || start.y > pageBottom) continue;
                t0 = 0f;
                t1 = 1f;
            } else {
                float topT = (pageTop - start.y) / dy;
                float bottomT = (pageBottom - start.y) / dy;
                t0 = Math.max(0f, Math.min(topT, bottomT));
                t1 = Math.min(1f, Math.max(topT, bottomT));
            }
            if (t1 <= t0) continue;
            parts.add(new PageLinePart(pageIdx, interpolate(start, end, t0), interpolate(start, end, t1), t0));
        }
        for (int i = 0; i < parts.size() - 1; i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                if (parts.get(i).startT > parts.get(j).startT) {
                    PageLinePart tmp = parts.get(i);
                    parts.set(i, parts.get(j));
                    parts.set(j, tmp);
                }
            }
        }
        return parts;
    }

    private void addAnnotationOnPage(int pageIdx, int type, Point[] localPts, float paintSize, int paintColor,
                                     int fallbackHeight, List<Integer> changedPages) {
        int pageW = getAnnotationPageWidth(pageIdx);
        int pageH = getAnnotationPageHeight(pageIdx, fallbackHeight);
        if (pageW <= 0 || pageH <= 0) return;
        core.addAnnotation(pageIdx, pageW, pageH, type, paintSize, paintColor, localPts);
        changedPages.add(pageIdx);
    }

    private int getAnnotationPageWidth(int pageIdx) {
        int pageW = mDocView.getPageDisplayWidth(pageIdx);
        if (pageW <= 0) pageW = mDocView.getWidth();
        return pageW;
    }

    private int getAnnotationPageHeight(int pageIdx, int fallbackHeight) {
        int pageH = mDocView.getPageDisplayHeight(pageIdx);
        if (pageH <= 0) pageH = fallbackHeight;
        return pageH;
    }

    private Point toPageLocalPoint(Point docPoint, int pageIdx) {
        return new Point(docPoint.x - mDocView.getPageScreenLeft(pageIdx),
                docPoint.y - mDocView.getPageDocTop(pageIdx));
    }

    private Point interpolate(Point start, Point end, float t) {
        return new Point(start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t);
    }

    private boolean samePoint(Point a, Point b) {
        return Math.abs(a.x - b.x) < 0.01f && Math.abs(a.y - b.y) < 0.01f;
    }

    private static class PagePointSegment {
        final int pageIdx;
        final List<Point> points = new ArrayList<>();

        PagePointSegment(int pageIdx) {
            this.pageIdx = pageIdx;
        }
    }

    private static class PageLinePart {
        final int pageIdx;
        final Point start;
        final Point end;
        final float startT;

        PageLinePart(int pageIdx, Point start, Point end, float startT) {
            this.pageIdx = pageIdx;
            this.start = start;
            this.end = end;
            this.startT = startT;
        }
    }

    private void showFreeTextDialog(final Point position) {
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.mupdf_freetext_title);

        final EditText editText = new EditText(this);
        editText.setHint(R.string.mupdf_freetext_hint);
        editText.setMinLines(2);

        alert.setView(editText);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_ensure), (dialog, which) -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty() && core != null) {
                int pageIdx = mDocView.findPageAtY((int) position.y);
                int pageTop = mDocView.getPageDocTop(pageIdx);
                int pageLeft = mDocView.getPageScreenLeft(pageIdx);
                PageView pageView = (PageView) mDocView.getView(pageIdx);
                int width = getAnnotationPageWidth(pageIdx);
                int height = mDocView.getPageDisplayHeight(pageIdx);
                if (height <= 0 && pageView != null) height = pageView.getHeight();
                if (width <= 0 || height <= 0) return;
                float[] color = core.parseColor(artBoard != null ? artBoard.getPaintColor() : Color.RED);
                Point localPosition = new Point(position.x - pageLeft, position.y - pageTop);
                core.addFreeTextAnnotation(pageIdx, width, height,
                        localPosition, text, "Helv", 16f, color);
                hadAnnotation = true;
                if (pageView != null) schedulePageUpdate(() -> pageView.update());
            }
            dialog.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    /**
     * 对所有已缓存/可见的页面应用最新的背景颜色滤镜。
     */
    private void refreshAllPageColorFilter() {
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                @Override
                public void applyToView(View view) {
                    if (view instanceof PageView) {
                        ((PageView) view).refreshColorFilter();
                    }
                }
            });
        }
    }

    private void applyWindowBrightness() {
        Window window = getWindow();
        if (window == null) return;

        int brightness = MupdfMacro.clampBrightness(MupdfMacro.brightness);
        WindowManager.LayoutParams attrs = window.getAttributes();
        if (brightness == 0) {
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            float percent = (brightness - MupdfMacro.MIN_BRIGHTNESS)
                    / (float) (MupdfMacro.MAX_BRIGHTNESS - MupdfMacro.MIN_BRIGHTNESS);
            attrs.screenBrightness = Math.max(0.01f, Math.min(1f, percent));
        }
        window.setAttributes(attrs);
    }

    /**
     * 背景颜色预设色板
     */
    private static final int[] BG_PRESET_COLORS = {
            0xFFFFFFFF, // 默认白
            0xFFF5E9D5, // 护眼米黄
            0xFFC7EDCC, // 护眼淡绿
            0xFFD9D9D9, // 淡灰
            0xFF40444B  // 夜间深灰
    };

    /**
     * 根据当前 {@link MupdfMacro#backgroundColor} 刷新色板选中态。
     */
    private void updateSwatchSelection(View[] swatches, float density) {
        for (int i = 0; i < swatches.length; i++) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(BG_PRESET_COLORS[i]);
            if (BG_PRESET_COLORS[i] == MupdfMacro.backgroundColor) {
                gd.setStroke((int) (3 * density), 0xFF2196F3);
            } else {
                gd.setStroke((int) (1 * density), 0xFFBBBBBB);
            }
            swatches[i].setBackground(gd);
        }
    }

    private int getFitPageZoomPercent() {
        if (core == null || mDocView == null || fullWidthScale <= 0) {
            return FIT_WIDTH_ZOOM_PERCENT;
        }
        int pageCount = core.countPages();
        if (pageCount <= 0) return FIT_WIDTH_ZOOM_PERCENT;
        int pageIndex = Math.max(0, Math.min(pageCount - 1, mDocView.getDisplayedViewIndex()));
        PointF pageSize = core.getPageSize(pageIndex);
        int viewWidth = mDocView.getWidth() > 0 ? mDocView.getWidth() : ScreenUtils.getScreenWidth(this);
        int viewHeight = mDocView.getHeight() > 0 ? mDocView.getHeight() : ScreenUtils.getScreenHeight(this);
        if (pageSize == null || pageSize.x <= 0 || pageSize.y <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return FIT_WIDTH_ZOOM_PERCENT;
        }

        float pageHeightAtFitWidth = viewWidth * (pageSize.y / pageSize.x);
        float fitPageScale = Math.min(1f, viewHeight / pageHeightAtFitWidth);
        int percent = Math.round(fitPageScale * 100f);//返回最接近参数的整数，并将关系四舍五入到正无穷大。
        return Math.max(ABSOLUTE_MIN_ZOOM_PERCENT, Math.min(FIT_WIDTH_ZOOM_PERCENT, percent));
    }

    private SharedPreferences getDisplaySettingsPrefs() {
        return getSharedPreferences(DISPLAY_SETTINGS_PREFS, Context.MODE_PRIVATE);
    }

    private int clampZoomPercent(int zoomPercent) {
        int minZoomPercent = getFitPageZoomPercent();
        return Math.max(minZoomPercent, Math.min(MAX_ZOOM_PERCENT, zoomPercent));
    }

    private int resolveInitialZoomPercent() {
        if (configuredZoomPercent >= 0) {
            return clampZoomPercent(configuredZoomPercent);
        }
        return getFitPageZoomPercent();
    }

    private void applyZoomPercent(int zoomPercent, boolean persist) {
        currentZoomPercent = clampZoomPercent(zoomPercent);
        if (mDocView != null) {
            mDocView.defaultScale(fullWidthScale * currentZoomPercent / 100f);
        }
        if (persist) {
            saveDisplaySettings();
        }
    }

    private void saveDisplaySettings() {
        getDisplaySettingsPrefs().edit()
                .putInt(PREF_BACKGROUND_COLOR, MupdfMacro.backgroundColor)
                .putInt(PREF_BRIGHTNESS, MupdfMacro.brightness)
                .putInt(PREF_ZOOM_PERCENT, currentZoomPercent)
                .apply();
    }

    private void applyWindowWatermark(String text) {
        mWindowWatermark = text == null ? "" : text.trim();
        if (mWindowWatermark.isEmpty()) {
            if (mWindowWatermarkView != null) {
                rootView.removeView(mWindowWatermarkView);
                mWindowWatermarkView = null;
            }
            return;
        }
        if (mWindowWatermarkColor == 0) {
            mWindowWatermarkColor = Color.parseColor("#33FFAB00");
        }
        if (mWindowWatermarkView == null) {
            mWindowWatermarkView = new WindowWatermarkView(this);
            mWindowWatermarkView.setLayoutParams(new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            rootView.addView(mWindowWatermarkView);
        }
        mWindowWatermarkView.setWatermark(mWindowWatermark, mWindowWatermarkColor);
        mWindowWatermarkView.bringToFront();
    }

    /**
     * 显示设置页：亮度、背景颜色、缩放。修改即时生效。
     */
    private void showSettingsDialog() {
        if (core == null) return;
        final float density = getResources().getDisplayMetrics().density;
        final int minZoomPercent = getFitPageZoomPercent();

        // 同步实际缩放（可能被双指缩放改变过），保证滑块与当前画面一致
        if (mDocView != null && fullWidthScale > 0) {
            int actual = Math.round(mDocView.getScale() / fullWidthScale * 100f);
            currentZoomPercent = Math.max(minZoomPercent, Math.min(MAX_ZOOM_PERCENT, actual));
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        //<editor-fold desc="亮度">
        root.addView(makeSectionLabel(getString(R.string.mupdf_brightness), density, false));

        LinearLayout brightnessRow = new LinearLayout(this);
        brightnessRow.setOrientation(LinearLayout.HORIZONTAL);
        brightnessRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final SeekBar brightnessBar = new SeekBar(this);
        brightnessBar.setMax(MupdfMacro.MAX_BRIGHTNESS - MupdfMacro.MIN_BRIGHTNESS);
        brightnessBar.setProgress(MupdfMacro.brightness - MupdfMacro.MIN_BRIGHTNESS);
        brightnessBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView brightnessValue = new TextView(this);
        brightnessValue.setWidth((int) (48 * density));
        brightnessValue.setGravity(android.view.Gravity.END);
        brightnessValue.setText(String.valueOf(MupdfMacro.brightness));

        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                MupdfMacro.brightness = progress + MupdfMacro.MIN_BRIGHTNESS;
                brightnessValue.setText(String.valueOf(MupdfMacro.brightness));
                applyWindowBrightness();
                saveDisplaySettings();
            }

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        brightnessRow.addView(brightnessBar);
        brightnessRow.addView(brightnessValue);
        root.addView(brightnessRow);
        //</editor-fold>

        //<editor-fold desc="背景颜色">
        root.addView(makeSectionLabel(getString(R.string.mupdf_background), density, true));

        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final View[] swatches = new View[BG_PRESET_COLORS.length];
        for (int i = 0; i < BG_PRESET_COLORS.length; i++) {
            final int color = BG_PRESET_COLORS[i];
            View sw = new View(this);
            int sz = (int) (40 * density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(0, 0, (int) (12 * density), 0);
            sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> {
                MupdfMacro.backgroundColor = color;
                refreshAllPageColorFilter();
                updateSwatchSelection(swatches, density);
                saveDisplaySettings();
            });
            swatches[i] = sw;
            swatchRow.addView(sw);
        }
        updateSwatchSelection(swatches, density);

        TextView customColor = new TextView(this);
        customColor.setText(getString(R.string.mupdf_custom_color));
        customColor.setTextColor(0xFF2196F3);
        customColor.setPadding((int) (8 * density), 0, 0, 0);
        customColor.setOnClickListener(v -> new MupdfColorPickerDialog(this,
                new MupdfColorPickerView.OnColorSubmitListener() {
                    @Override
                    public void submitColor(int color) {
                        // 自定义颜色保留 alpha 全不透明
                        MupdfMacro.backgroundColor = 0xFF000000 | (color & 0x00FFFFFF);
                        refreshAllPageColorFilter();
                        updateSwatchSelection(swatches, density);
                        saveDisplaySettings();
                    }
                }, MupdfMacro.backgroundColor).show());
        swatchRow.addView(customColor);
        root.addView(swatchRow);
        //</editor-fold>

        //<editor-fold desc="缩放">
        root.addView(makeSectionLabel(getString(R.string.mupdf_zoom), density, true));

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);
        zoomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final SeekBar zoomBar = new SeekBar(this);
        int zoomRange = MAX_ZOOM_PERCENT - minZoomPercent;
        zoomBar.setMax(zoomRange);
        zoomBar.setProgress(Math.max(0, Math.min(zoomRange, currentZoomPercent - minZoomPercent)));
        zoomBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView zoomValue = new TextView(this);
        zoomValue.setWidth((int) (56 * density));
        zoomValue.setGravity(android.view.Gravity.END);
        zoomValue.setText(currentZoomPercent + "%");

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentZoomPercent = progress + minZoomPercent;
                zoomValue.setText(currentZoomPercent + "%");
                if (fromUser && mDocView != null) {
                    applyZoomPercent(currentZoomPercent, true);
                }
            }

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        zoomRow.addView(zoomBar);
        zoomRow.addView(zoomValue);
        root.addView(zoomRow);
        //</editor-fold>

        if (mWindowWatermarkEnabled) {
            //<editor-fold desc="水印">
            root.addView(makeSectionLabel(getString(R.string.mupdf_watermark), density, true));

            android.widget.RadioGroup watermarkModeGroup = new android.widget.RadioGroup(this);
            watermarkModeGroup.setOrientation(android.widget.RadioGroup.HORIZONTAL);
            watermarkModeGroup.setGravity(android.view.Gravity.CENTER_VERTICAL);

            android.widget.RadioButton noWatermark = new android.widget.RadioButton(this);
            noWatermark.setId(View.generateViewId());
            noWatermark.setText(R.string.mupdf_watermark_none);

            android.widget.RadioButton pdfWatermark = new android.widget.RadioButton(this);
            pdfWatermark.setId(View.generateViewId());
            pdfWatermark.setText(R.string.mupdf_watermark_pdf);

            android.widget.RadioButton windowWatermark = new android.widget.RadioButton(this);
            windowWatermark.setId(View.generateViewId());
            windowWatermark.setText(R.string.mupdf_watermark_window);

            watermarkModeGroup.addView(noWatermark);
            watermarkModeGroup.addView(pdfWatermark);
            watermarkModeGroup.addView(windowWatermark);
            root.addView(watermarkModeGroup);

            final EditText watermarkInput = new EditText(this);
            watermarkInput.setSingleLine();
            watermarkInput.setHint(R.string.mupdf_watermark_text_hint);
            watermarkInput.setText(!TextUtils.isEmpty(mWindowWatermark) ? mWindowWatermark : (!TextUtils.isEmpty(mWatermark) ? mWatermark : ""));
            watermarkInput.setInputType(InputType.TYPE_CLASS_TEXT);
            root.addView(watermarkInput);

            final int[] watermarkMode = {
                    !TextUtils.isEmpty(mWindowWatermark) || !TextUtils.isEmpty(mWatermark) ? WATERMARK_MODE_WINDOW : WATERMARK_MODE_NONE
            };
            watermarkModeGroup.check(watermarkMode[0] == WATERMARK_MODE_WINDOW
                    ? windowWatermark.getId()
                    : noWatermark.getId());
            watermarkInput.setEnabled(watermarkMode[0] != WATERMARK_MODE_NONE);
            watermarkModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == pdfWatermark.getId()) {
                    watermarkMode[0] = WATERMARK_MODE_PDF;
                } else if (checkedId == windowWatermark.getId()) {
                    watermarkMode[0] = WATERMARK_MODE_WINDOW;
                } else {
                    watermarkMode[0] = WATERMARK_MODE_NONE;
                }
                watermarkInput.setEnabled(watermarkMode[0] != WATERMARK_MODE_NONE);
            });

            TextView applyWatermarkBtn = new TextView(this);
            applyWatermarkBtn.setText(getString(R.string.mupdf_watermark_apply));
            applyWatermarkBtn.setTextColor(0xFF2196F3);
            applyWatermarkBtn.setGravity(android.view.Gravity.CENTER);
            applyWatermarkBtn.setPadding(0, (int) (12 * density), 0, 0);
            applyWatermarkBtn.setOnClickListener(v -> {
                String text = watermarkInput.getText() == null ? "" : watermarkInput.getText().toString().trim();
                if (watermarkMode[0] == WATERMARK_MODE_NONE) {
                    applyWindowWatermark("");
                    mWatermark = "";
                    Toast.makeText(this, getString(R.string.mupdf_watermark_none), Toast.LENGTH_SHORT).show();
                } else if (watermarkMode[0] == WATERMARK_MODE_WINDOW) {
                    if (text.isEmpty()) {
                        Toast.makeText(this, R.string.mupdf_watermark_text_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mWatermark = "";
                    applyWindowWatermark(text);
                    Toast.makeText(this, getString(R.string.mupdf_watermark_window) + " " + getString(R.string.mupdf_art_done), Toast.LENGTH_SHORT).show();
                } else {
                    if (text.isEmpty()) {
                        Toast.makeText(this, R.string.mupdf_watermark_text_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float[] color = new float[]{0.82f, 0.20f, 0.18f};
                    core.addContentWatermark(text, 0f, 45f, 0.15f, color, 1.5f);
                    hadAnnotation = true;
                    afterAnnotationPreservingScroll();
                    Toast.makeText(this, getString(R.string.mupdf_watermark_pdf) + " " + getString(R.string.mupdf_art_done), Toast.LENGTH_SHORT).show();
                }
            });
            root.addView(applyWatermarkBtn);
            //</editor-fold>
        }


        // 重置按钮（不关闭弹窗，直接复位三项）
        TextView resetBtn = new TextView(this);
        resetBtn.setText(getString(R.string.mupdf_reset));
        resetBtn.setTextColor(0xFF2196F3);
        resetBtn.setGravity(android.view.Gravity.CENTER);
        resetBtn.setPadding(0, (int) (16 * density), 0, 0);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.topMargin = (int) (8 * density);
        resetBtn.setLayoutParams(resetLp);
        resetBtn.setOnClickListener(v -> {
            brightnessBar.setProgress(255); // 亮度归零
            zoomBar.setProgress(FIT_WIDTH_ZOOM_PERCENT - minZoomPercent); // 100%
            MupdfMacro.backgroundColor = MupdfMacro.DEFAULT_BACKGROUND_COLOR;
            currentZoomPercent = FIT_WIDTH_ZOOM_PERCENT;
            if (mDocView != null) {
                mDocView.defaultScale(fullWidthScale);
            }
            applyWindowBrightness();
            refreshAllPageColorFilter();
            updateSwatchSelection(swatches, density);
            zoomValue.setText(FIT_WIDTH_ZOOM_PERCENT + "%");
            saveDisplaySettings();
        });
        root.addView(resetBtn);

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.mupdf_setting_title);
        alert.setView(scroll);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_close),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    /**
     * 构造设置项的小标题。
     */
    private TextView makeSectionLabel(String text, float density, boolean topMargin) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFF333333);
        label.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (topMargin) lp.topMargin = (int) (16 * density);
        lp.bottomMargin = (int) (4 * density);
        label.setLayoutParams(lp);
        return label;
    }

    private void showWatermarkDialog() {
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.mupdf_watermark_title);

        final EditText editText = new EditText(this);
        editText.setHint(R.string.mupdf_watermark_text_hint);
        editText.setSingleLine();

        alert.setView(editText);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_ensure), (dialog, which) -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty() && core != null) {
                float[] color = new float[]{0.82f, 0.20f, 0.18f};
                core.addContentWatermark(text, 0f, 45f, 0.15f, color, 1.5f);
                hadAnnotation = true;
                afterAnnotationPreservingScroll();
                Toast.makeText(MuPdfDocumentActivity.this,
                        getString(R.string.mupdf_watermark) + " " + getString(R.string.mupdf_art_done),
                        Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    private void showSignTableDialog() {
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.mupdf_sign_table_title);

        final EditText editText = new EditText(this);
        editText.setHint(R.string.mupdf_sign_table_hint);
        editText.setMinLines(3);
        editText.setGravity(android.view.Gravity.TOP);

        alert.setView(editText);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_sign_table_create), (dialog, which) -> {
            String input = editText.getText().toString().trim();
            if (!input.isEmpty() && core != null) {
                String[] names = input.split("[,\n]");
                java.util.ArrayList<String> nameList = new java.util.ArrayList<>();
                for (String n : names) {
                    String trimmed = n.trim();
                    if (!trimmed.isEmpty()) nameList.add(trimmed);
                }
                if (!nameList.isEmpty()) {
                    signTableTotalNames = nameList.size();
                    core.createSignatureTable(nameList.toArray(new String[0]),
                            "姓名", "时间", "签名");
                    hadAnnotation = true;
                    refreshDocumentAndShowPage(core.countPages() - 1);
                    Toast.makeText(MuPdfDocumentActivity.this,
                            getString(R.string.mupdf_sign_table) + " " + getString(R.string.mupdf_art_done),
                            Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    private void showSignRowDialog() {
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.mupdf_sign_row_title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        final EditText rowInput = new EditText(this);
        rowInput.setHint(R.string.mupdf_sign_row_hint);
        rowInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(rowInput);

        final EditText timeInput = new EditText(this);
        timeInput.setHint(R.string.mupdf_sign_row_hint2);
        timeInput.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        layout.addView(timeInput);

        alert.setView(layout);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mupdf_sign_row_draw), (dialog, which) -> {
            String rowStr = rowInput.getText().toString().trim();
            String timeStr = timeInput.getText().toString().trim();
            if (!rowStr.isEmpty() && core != null) {
                int rowIndex = Integer.parseInt(rowStr);
                final int targetPage = Math.max(0, core.countPages() - 1);
                // 打开签名画板
                new ArtBoardDialog(MuPdfDocumentActivity.this, false, new ArtBoardDialog.SignatureListener() {
                    @Override
                    public void onSuccess(Object[] object) {
                        List<SignatureBoard.DrawPath> drawPaths = (List<SignatureBoard.DrawPath>) object[0];
                        RectF regionSize = (RectF) object[1];
                        // 生成签名图片
                        Bitmap bmp = renderSignatureBitmap(drawPaths, regionSize);
                        if (bmp != null) {
                            int w = bmp.getWidth();
                            int h = bmp.getHeight();
                            int[] pixels = new int[w * h];
                            bmp.getPixels(pixels, 0, w, 0, 0, w, h);
                            byte[] rgb = new byte[w * h * 3];
                            for (int i = 0; i < pixels.length; i++) {
                                int px = pixels[i];
                                rgb[i * 3] = (byte) ((px >> 16) & 0xFF);
                                rgb[i * 3 + 1] = (byte) ((px >> 8) & 0xFF);
                                rgb[i * 3 + 2] = (byte) (px & 0xFF);
                            }
                            core.setSignatureRow(rowIndex, timeStr, rgb, w, h, signTableTotalNames);
                            hadAnnotation = true;
                            refreshDocumentAndShowPage(targetPage);
                            Toast.makeText(MuPdfDocumentActivity.this,
                                    getString(R.string.mupdf_sign_row) + " " + getString(R.string.mupdf_art_done),
                                    Toast.LENGTH_SHORT).show();
                            bmp.recycle();
                        }
                    }
                }).show();
            }
            dialog.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    private android.graphics.Bitmap renderSignatureBitmap(List<SignatureBoard.DrawPath> paths, RectF region) {
        int w = (int) (region.right - region.left + 20);
        int h = (int) (region.bottom - region.top + 20);
        if (w <= 0 || h <= 0) return null;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        for (SignatureBoard.DrawPath dp : paths) {
            android.graphics.Paint p = new android.graphics.Paint();
            p.setAntiAlias(true);
            p.setDither(true);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setColor(dp.color);
            p.setStrokeWidth(3f);
            android.graphics.Path path = new android.graphics.Path();
            for (int i = 0; i < dp.points.length; i++) {
                float x = dp.points[i].x - region.left + 10;
                float y = dp.points[i].y - region.top + 10;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, p);
        }
        return bmp;
    }

    private void setViewShowState(View view, boolean visible) {
        setViewShowState(view, visible ? View.VISIBLE : View.GONE);
    }

    private void setViewShowState(View view, int visibility) {
        if (view != null) view.setVisibility(visibility);
    }

    public void onDestroy() {
        Debugger.i(TAG, "---onDestroy---start");
        if (MupdfMacro.isSharing) {
            EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_exit_annotation).objects(mediaId).build());
        }
        if (thumbnailAdapter != null) {
            thumbnailAdapter.clearCache();
            thumbnailAdapter.shutdownExecutor();
            thumbnailAdapter = null;
        }
        ActUtil.removeActivity(this);
        unregisterEventBus();
        MupdfMacro.isSharing = false;
        MupdfMacro.launchSrcmemid = 0;
        MupdfMacro.launchSrcwbid = 0;
        MupdfMacro.sharingIds.clear();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        pendingPageUpdate = null;
        mainHandler = null;
        if (mDocView != null) {
            Debugger.i(TAG, "---onDestroy---ReaderView releaseAllBitmaps");
            mDocView.releaseAllBitmaps();
        }
        if (core != null) {
            Debugger.i(TAG, "---onDestroy---MuPDFCore");
            core.onDestroy();
        }
        core = null;
        if (deleteFileWhenExit) {
            if (srcFilePath != null && !srcFilePath.isEmpty()) {
                File file = new File(srcFilePath);
                if (file.exists()) {
                    boolean delete = file.delete();
                    Debugger.i("退出pdf预览时删除源文件：" + delete);
                }
            }
        }
        Debugger.i(TAG, "---onDestroy---end");
        super.onDestroy();
    }

}
