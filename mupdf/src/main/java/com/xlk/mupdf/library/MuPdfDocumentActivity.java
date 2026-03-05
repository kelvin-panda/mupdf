package com.xlk.mupdf.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <p>mupdf文档查看器</p>
 * 需要取消屏幕适配： AutoSizeConfig.getInstance().getExternalAdaptManager().addCancelAdaptOfActivity(MuPdfDocumentActivity.class);
 */
public class MuPdfDocumentActivity extends AppCompatActivity {
    private static final String TAG = "MuPdfDocumentActivity";

    //<editor-fold desc="成员变量">
    private TextView inkSizeTextView;
    private LinearLayout inkSizeLayout;
    private ImageButton outlineButton, annotationButton, refreshButton,
            revokeButton, deleteButton, colorButton, penButton, inkSizeButton, deleteLineButton,
            lineButton, inviteButton, highlightButton, outOpen, uploadButton, exitMupdfButton, screenshotButton, signatureButton, doneButton, exitButton;
    private String srcFilePath, annotationSavePath, srcUri, mWatermark;
    private int mWatermarkColor;
    private int mediaId;
    private boolean uploadEnable, annotationEnable, signatureEnable, captureEnable, wpsOpenEnable,
            mAnnotationVisible, mInkSizeViewVisible, afterAnnotationRefresh;
    private AnnotationArtBoard artBoard;
    private SeekBar inkSizeSeekBar;
    private RelativeLayout mRootLayout;
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
    private boolean deleteFileWhenExit;
    private boolean isOnlyPreview;
    /**
     * 批注后上传的目录id
     */
    private int uploadDirId = 2;
    private int srcPageIndex = 0;
    private boolean isFullScreen = true;

    /* The core rendering instance */
    enum TopBarMode {Main, More}

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int OUTLINE_REQUEST = 0;
    private MuPDFCore core;
    private String mDocTitle;
    private String mDocKey;
    private ReaderView mDocView;
    private RelativeLayout rootView;
    private TextView mTvMark;
    private View mButtonsView;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mDocNameView;
    private LinearLayout mLlPageView;
    private TextView mPageNumberView, mPrePageView, mNextPageView;
    private ViewAnimator mTopBarSwitcher, inkOperationSwitcher;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private AlertDialog.Builder mAlertBuilder;
    private ArrayList<OutlineActivity.Item> mFlatOutline;
    private boolean mReturnToLibraryActivity = false;

    protected int mDisplayDPI;
    private int mLayoutEM = 10;
    private int mLayoutW = 312;
    private int mLayoutH = 504;

    protected View mLayoutButton;
    protected PopupMenu mLayoutPopupMenu;
    public static List<AnnotationBean> inkAnnotations = new ArrayList<>();
    /**
     * 点击了保存按钮
     */
    private boolean saveWhenExit;
    /**
     * 有进行批注
     */
    private boolean hadAnnotation;

    /**
     * 当前页：索引
     */
    private int currentPageIndex = 0;
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

                    boolean watermark = bundle.getBoolean(MupdfMacro.bundle_key_watermark_enable, false);
                    if (watermark) {
                        mWatermark = bundle.getString(MupdfMacro.bundle_key_watermark_content, "");
                        mWatermarkColor = bundle.getInt(MupdfMacro.bundle_key_watermark_color, Color.parseColor("#66FF6D00"));
                    }
                    Debugger.i(TAG, "c bundle："
                            + "\nsrcFilePath=" + srcFilePath
                            + "\nsrcUri=" + srcUri
                            + "\nuri=" + uri
                            + "\nmediaId=" + mediaId
                            + "\ndeleteFileWhenExit=" + deleteFileWhenExit
                            + "\nisOnlyPreview=" + isOnlyPreview
                            + "\nuploadDirId=" + uploadDirId
                            + "\nwatermark=" + watermark
                            + "\nmWatermark=" + mWatermark
                            + "\nMupdfMacro.clarityLimitMode=" + MupdfMacro.clarityLimitMode
                            + "\nisFullScreen=" + isFullScreen
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
        createUI(savedInstanceState);
        registerEventBus();
        ActUtil.addActivity(this);
    }

    float fullWidthScale = 1.0f;

    public void createUI(Bundle savedInstanceState) {
        if (core == null)
            return;
        if (isFullScreen) {
            // 计算宽度占满时的缩放比例
            PointF size = core.getPageSize(0);
            int screenWidth = ScreenUtils.getScreenWidth(this);
            int screenHeight = ScreenUtils.getScreenHeight(this);
            float mSourceScale = Math.min(screenWidth / size.x, screenHeight / size.y);
            android.graphics.Point newSize = new android.graphics.Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));
            fullWidthScale = screenWidth * 1.0f / (newSize.x * 1.0f);
            Debugger.i(TAG, "createUI: size=" + size + ",newSize=" + newSize + ",fullWidthScale=" + fullWidthScale);
        }
        mDocView = new ReaderView(this, fullWidthScale) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                currentPageIndex = i;
                mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", i + 1, core.countPages()));
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
                hideButtons();
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
        PageAdapter pageAdapter = new PageAdapter(this, core, fullWidthScale, "");
        mDocView.setAdapter(pageAdapter);

        extracted(savedInstanceState);

        // Stick the document view and the buttons overlay into a parent view
        mRootLayout = new RelativeLayout(this);
        mRootLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRootLayout.setBackgroundColor(Color.DKGRAY);
        mRootLayout.addView(mDocView);
        mRootLayout.addView(mButtonsView);
        if (mWatermark != null && !mWatermark.isEmpty()) {
            mTvMark.setVisibility(View.VISIBLE);
            mTvMark.setText(mWatermark);
            mTvMark.setTextColor(mWatermarkColor);
            mTvMark.invalidate();
        } else {
            mTvMark.setVisibility(View.GONE);
        }
        setContentView(mRootLayout);

        mainHandler.postDelayed(() -> {
            if (srcPageIndex != 0) {
                mDocView.setDisplayedViewIndex(srcPageIndex);
            }
            mDocView.defaultScale(fullWidthScale);
            mDocView.requestLayout();
            mDocView.run();
        }, 500L);

        Debugger.i(TAG, "createUI: end");
    }

    private void extracted(Bundle savedInstanceState) {
        makeButtonsView();
        if (MupdfMacro.shareAnnotationEnable) {
            //有文件id才显示
            inviteButton.setVisibility(mediaId != 0 ? View.VISIBLE : View.GONE);
        }
        //文件名称
        mDocNameView.setText(mDocTitle);

        //退出pdf预览
        exitMupdfButton.setOnClickListener(v -> {
            exit();
        });
        //刷新，重新加载当前页
        refreshButton.setOnClickListener(v -> {
            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
            core.logAnnotations(0);
        });
        //退出文档批注上传开关
        uploadButton.setOnClickListener(v -> {
            saveWhenExit = !saveWhenExit;
            toast(saveWhenExit ? "将在退出时保存到批注目录" : "已取消退出时保存到批注目录");
        });
        uploadButton.setVisibility(uploadEnable ? View.VISIBLE : View.GONE);

        //<editor-fold desc="签名操作">
        //签名
        signatureButton.setOnClickListener(v -> {
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
        signatureButton.setVisibility(signatureEnable ? View.VISIBLE : View.GONE);
        //提交签名
        tv_submit_signature.setOnClickListener(v -> {
            List<SignatureBoard.DrawPath> drawPaths = mScalableView.getDrawPaths();
            PageView pageView = (PageView) mDocView.getDisplayedView();
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
                Point[] percentPoints = core.addAnnotation(mDocView.mCurrent, width, height, PDFAnnotation.TYPE_INK, 5 / 3.0f, drawPath.color, array);
                //points是经过core.addAnnotation方法计算后的实际坐标
                annotationBeans.add(new MupdfAnnotationBean(mediaId, mDocView.mCurrent + 1, PDFAnnotation.TYPE_INK, 5 / 3.0f, drawPath.color, percentPoints));
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
            mDocView.afterAnnotation();
        });
        //取消签名
        tv_cancel_signature.setOnClickListener(v -> {
            mCurPageView.removeView(mScalableView);
            mScalableView = null;
            mCurPageView = null;
            ll_signature_layout.setVisibility(View.GONE);
            isSigning = false;
            mDocView.setSigning(false);
        });
        //</editor-fold>

        //外部打开
        outOpen.setOnClickListener(v -> {
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
        outOpen.setVisibility(wpsOpenEnable ? View.VISIBLE : View.GONE);
        //截图批注
        screenshotButton.setOnClickListener(v -> {
            hideButtons();
            mainHandler.postDelayed(() -> {
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_screenshot).objects(mDocTitle, 0).build());
            }, 250);
        });
        screenshotButton.setVisibility(captureEnable ? View.VISIBLE : View.GONE);
        //界面跳转
        mPageNumberView.setOnClickListener(v -> {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle("调整到指定页");
            EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            alert.setView(editText);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, "确定", (dialog, which) -> {
                String number = editText.getText().toString().trim();
                if (number.isEmpty()) {
                    toast("请输入页码");
                    return;
                }
                int max = core.countPages();
                int value = Integer.parseInt(number);
                if (value < 1) {
                    toast("页码最小值为1");
                    return;
                }
                if (value > max) {
                    toast("不能大于最大页");
                    return;
                }
                mDocView.setDisplayedViewIndex(value - 1);
                dialog.dismiss();
            });
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialog, which) -> dialog.dismiss());
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
            outlineButton.setOnClickListener(new View.OnClickListener() {
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
            outlineButton.setVisibility(View.GONE);
        }

        //<editor-fold desc="批注">
        //开启批注
        annotationButton.setOnClickListener(v -> {
            hideButtons();
            showAnnotationViews();
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            Debugger.i(TAG, "开启批注:(" + width + "," + height + ")");
            chooseType(1);
            artBoard = new AnnotationArtBoard(this, core, mDocView, width, height, new AnnotationArtBoard.DrawExitListener() {
                @Override
                public void onDrawAnnotations(List<AnnotationBean> inkAnnotations) {
                    Debugger.i(TAG, "onDrawAnnotations 将要绘制的批注数量： " + inkAnnotations.size() + ",MupdfMacro.isSharing=" + MupdfMacro.isSharing);
                    if (!inkAnnotations.isEmpty()) {
                        List<MupdfAnnotationBean> annotationBeans = new ArrayList<>();
                        for (AnnotationBean inkAnnotation : inkAnnotations) {
                            Point[] points = inkAnnotation.getPoints();
                            float paintSize = inkAnnotation.getPaintSize();
                            int paintColor = inkAnnotation.getPaintColor();
                            int type = inkAnnotation.getType();
                            paintSize = paintSize / 3.0f;
                            Point[] percentPoints = core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);

                            if (MupdfMacro.isSharing) {
                                //points是经过core.addAnnotation方法计算后的实际坐标
                                annotationBeans.add(new MupdfAnnotationBean(mediaId, mDocView.mCurrent + 1, type, paintSize, paintColor, percentPoints));
                            }
                        }
                        if (MupdfMacro.isSharing && !annotationBeans.isEmpty()) {
                            Debugger.i(TAG, "onDrawAnnotations 将要共享绘制的批注数量： " + annotationBeans.size());
                            EventBus.getDefault().post(new MupdfEventMessage.Builder()
                                    .type(MupdfBusType.inform_share_annotation)
                                    .objects(annotationBeans)
                                    .build());
                        }
                        Debugger.i(TAG, "onDrawAnnotations 批注完刷新页面");
                        //批注后进行实时显示出来
                        //方式一：无效
//                        PageView displayedView = (PageView) mDocView.getDisplayedView();
//                        if (displayedView != null) {
//                            displayedView.update();
//                        }

                        //方式二：该方式有效，但是整个画面会重新加载且会回到页面顶部
//                        mDocView.setDisplayedViewIndex(mDocView.mCurrent);

                        //方式三：
                        mDocView.afterAnnotation();
                        hadAnnotation = true;
                    }
                }
            });
            artBoard.setPaintWidth(default_ink_size);
            pageView.addView(artBoard);
            artBoard.layout(0, 0, width, height);
        });
        annotationButton.setVisibility(annotationEnable ? View.VISIBLE : View.GONE);

        ibs.add(deleteButton);//删除
        ibs.add(penButton);//墨迹
        ibs.add(lineButton);//直线
        ibs.add(deleteLineButton);//删除线
        ibs.add(highlightButton);//高亮，矩形
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            inkSizeSeekBar.setMin(1);
        }
        inkSizeSeekBar.setMax(100);
        inkSizeTextView.setText(String.valueOf(default_ink_size));
        inkSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = Math.max(progress, 1);
                inkSizeTextView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                progress = Math.max(progress, 1);
                inkSizeSeekBar.setProgress(progress);
                artBoard.setPaintWidth(progress);
                inkSizeTextView.setText(String.valueOf(progress));
            }
        });
        //颜色
        colorButton.setOnClickListener(v -> {
            new MupdfColorPickerDialog(this, new MupdfColorPickerView.OnColorSubmitListener() {
                @Override
                public void submitColor(int color) {
                    artBoard.setPaintColor(color);
                }
            }, Color.RED).show();
        });
        //撤销
        revokeButton.setOnClickListener(v -> {
            if (artBoard != null) {
                artBoard.revoke();
            }
        });
        //删除
        deleteButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_ERASER) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_ERASER);
                    chooseType(0);
                }
            }
        });
        //画笔
        penButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_SLINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_SLINE);
                    chooseType(1);
                }
            }
        });
        //画笔大小
        inkSizeButton.setOnClickListener(v -> {
            if (mInkSizeViewVisible) {
                hideInkSizeViews();
            } else {
                showInkSizeViews();
            }
        });
        //直线
        lineButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_LINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_LINE);
                    chooseType(2);
                }
            }
        });
        //删除线、下划线
        deleteLineButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_DELLINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_DELLINE);
                    chooseType(3);
                }
            }
        });
        //高亮
        highlightButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_RECT) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_RECT);
                    chooseType(4);
                }
            }
        });
        //邀请多人批注
        inviteButton.setOnClickListener(v -> {
            Debugger.e("邀请多人批注");
            EventBus.getDefault().post(new MupdfEventMessage.Builder()
                    .type(MupdfBusType.inform_invite_annotation)
                    .objects(mDocTitle, mediaId, currentPageIndex + 1)
                    .build());
        });
        //提交批注
        doneButton.setOnClickListener(v -> {
            hideAnnotationViews();
        });
        //取消批注
        exitButton.setOnClickListener(v -> {
            artBoard.setCancelAnnotation();
            hideAnnotationViews();
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
                        mDocView.afterAnnotation();
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

    private final List<ImageButton> ibs = new ArrayList<>();

    private void chooseType(int index) {
        for (int i = 0; i < ibs.size(); i++) {
            boolean selected = index == i;
            ImageButton imageButton = ibs.get(i);
            if (selected) {
                imageButton.getDrawable().setTint(Color.argb(255, 33, 150, 243));
            } else {
                imageButton.getDrawable().setTint(Color.argb(255, 255, 255, 255));
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
    }

    @Override
    protected void onPause() {
        super.onPause();

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
        if (!inkAnnotations.isEmpty()) {
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            for (AnnotationBean inkAnnotation : inkAnnotations) {
                Point[] points = inkAnnotation.getPoints();
                float paintSize = inkAnnotation.getPaintSize();
                int paintColor = inkAnnotation.getPaintColor();
                int type = inkAnnotation.getType();
                paintSize = paintSize / 3.0f;
                core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);
            }
            //绘制到pdf文件后清空
            inkAnnotations.clear();
            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
        }
    }

    private void toast(String msg) {
        try {
            mainHandler.post(() -> {
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

    private void showAnnotationViews() {
        if (!mAnnotationVisible) {
            mAnnotationVisible = true;
            Animation anim = new TranslateAnimation(inkOperationSwitcher.getWidth(), 0, 0, 0);
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

    private void hideAnnotationViews() {
        if (mAnnotationVisible) {
            mAnnotationVisible = false;
            Animation anim = new TranslateAnimation(0, inkOperationSwitcher.getWidth(), 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(false);
                    inkOperationSwitcher.setVisibility(View.INVISIBLE);
                    hideInkSizeViews();
                    inkSizeSeekBar.setProgress(default_ink_size);
                    if (artBoard != null) {
                        ((PageView) mDocView.getDisplayedView()).removeView(artBoard);
                        artBoard.clear();
                        artBoard.release();
                        artBoard = null;
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    if (afterAnnotationRefresh) {
                        Debugger.e("批注期间有收到别人的共享批注，现在进行刷新");
                        afterAnnotationRefresh = false;
                        mDocView.afterAnnotation();
                        //mDocView.setDisplayedViewIndex(mDocView.mCurrent);
                    }
                }
            });
            inkOperationSwitcher.startAnimation(anim);
        }
    }

    private void showInkSizeViews() {
        if (!mInkSizeViewVisible) {
//            mInkSizeViewVisible = true;
//            inkSizeLayout.setVisibility(View.VISIBLE);
            Animation anim = new AlphaAnimation(0, 1f);
//            Animation anim = new TranslateAnimation(inkSizeLayout.getWidth(), 0, 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    inkSizeLayout.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mInkSizeViewVisible = true;
                }
            });
            inkSizeLayout.startAnimation(anim);
        }
    }

    private void hideInkSizeViews() {
        if (mInkSizeViewVisible) {
//            mInkSizeViewVisible = false;
//            inkSizeLayout.setVisibility(View.GONE);
            Animation anim = new AlphaAnimation(1f, 0f);
//            Animation anim = new TranslateAnimation(0, inkSizeLayout.getWidth(), 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mInkSizeViewVisible = false;
                    inkSizeLayout.setVisibility(View.GONE);
                }
            });
            inkSizeLayout.startAnimation(anim);
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        currentPageIndex = index;
        if (mPageNumberView != null)
            mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", index + 1, core.countPages()));
    }

    private void makeButtonsView() {
        int layoutResId = MupdfMacro.isHengXunVersion ? R.layout.mupdf_document_activity_hengxun : R.layout.mupdf_document_activity_default;
        mButtonsView = getLayoutInflater().inflate(layoutResId, null);
        rootView = (RelativeLayout) mButtonsView.findViewById(R.id.rootView);
        mTvMark = (TextView) mButtonsView.findViewById(R.id.tv_mark);
        mDocNameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
        mLlPageView = (LinearLayout) mButtonsView.findViewById(R.id.ll_page_view);
        mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);//页码
        mPrePageView = (TextView) mButtonsView.findViewById(R.id.prePage);//上一页
        mNextPageView = (TextView) mButtonsView.findViewById(R.id.nextPage);//下一页
        mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(R.id.switcher);
        mLayoutButton = mButtonsView.findViewById(R.id.layoutButton);
        //提交签名、取消签名
        ll_signature_layout = mButtonsView.findViewById(R.id.ll_signature_layout);
        tv_submit_signature = mButtonsView.findViewById(R.id.tv_submit_signature);
        tv_cancel_signature = mButtonsView.findViewById(R.id.tv_cancel_signature);

        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mLlPageView.setVisibility(View.INVISIBLE);

        // 自定义组件
        uploadButton = mButtonsView.findViewById(R.id.uploadButton);
        refreshButton = mButtonsView.findViewById(R.id.refreshButton);
        outOpen = mButtonsView.findViewById(R.id.outOpen);
        screenshotButton = mButtonsView.findViewById(R.id.screenshotButton);
        signatureButton = mButtonsView.findViewById(R.id.signatureButton);
        annotationButton = mButtonsView.findViewById(R.id.annotationButton);
        outlineButton = mButtonsView.findViewById(R.id.outlineButton);
        exitMupdfButton = mButtonsView.findViewById(R.id.exitMupdfButton);

        //<editor-fold desc="批注控件">
        inkOperationSwitcher = mButtonsView.findViewById(R.id.inkOperationSwitcher);
        //关闭
        exitButton = mButtonsView.findViewById(R.id.exitButton);
        //画笔
        penButton = mButtonsView.findViewById(R.id.penButton);
        //粗细
        inkSizeButton = mButtonsView.findViewById(R.id.inkSizeButton);
        //直线
        lineButton = mButtonsView.findViewById(R.id.lineButton);
        //删除
        deleteButton = mButtonsView.findViewById(R.id.deleteButton);
        //颜色
        colorButton = mButtonsView.findViewById(R.id.colorButton);
        //共享
        inviteButton = mButtonsView.findViewById(R.id.inviteButton);
        //下划线，删除线
        deleteLineButton = mButtonsView.findViewById(R.id.deleteLineButton);
        //高亮
        highlightButton = mButtonsView.findViewById(R.id.highlightButton);
        //撤销
        revokeButton = mButtonsView.findViewById(R.id.revokeButton);
        //确定
        doneButton = mButtonsView.findViewById(R.id.doneButton);

        //画笔大小
        inkSizeLayout = mButtonsView.findViewById(R.id.inkSizeLayout);
        inkSizeSeekBar = mButtonsView.findViewById(R.id.inkSizeSeekBar);
        inkSizeTextView = mButtonsView.findViewById(R.id.inkSizeTextView);
        //</editor-fold>

//        inkOperationSwitcher.setVisibility(View.INVISIBLE);

        if (isOnlyPreview) {
            screenshotButton.setVisibility(View.GONE);
            uploadButton.setVisibility(View.GONE);
            signatureButton.setVisibility(View.GONE);
            annotationButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onSearchRequested() {
//        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
//            hideButtons();
//        } else {
//            showButtons();
//            searchModeOn();
//        }
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
        if (hadAnnotation) {
            tipSavePop();
        } else {
            finish();
        }
//        if (mDocView == null || (mDocView != null && !mDocView.popHistory())) {
//            super.onBackPressed();
//            if (mReturnToLibraryActivity) {
//                Intent intent = getPackageManager().getLaunchIntentForPackage(getComponentName().getPackageName());
//                startActivity(intent);
//            }
//        }
    }

    private void exit() {
        Debugger.i(TAG, "---exit---");
        if (saveWhenExit && hadAnnotation) {
            saveAndExit();
        } else {
            finish();
        }
    }

    private void tipSavePop() {
        new AlertDialog.Builder(this)
                .setTitle("文件已修改，是否保存到批注目录？")
                .setPositiveButton("保存并退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        saveAndExit();
                    }
                })
                .setNegativeButton("直接退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .create()
                .show();
    }

    private void saveAndExit() {
        ProgressDialog progressDialog = new ProgressDialog(MuPdfDocumentActivity.this);
        progressDialog.setMessage("请稍后...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        new Thread(() -> {
            try {
                long l = System.currentTimeMillis();
                String savePath = core.save(srcFilePath, annotationSavePath);
                Debugger.i(TAG, "保存用时：" + (System.currentTimeMillis() - l) + ",savePath=" + savePath);
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_upload).objects(savePath, uploadDirId).build());
                mainHandler.postDelayed(() -> {
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

    public void onDestroy() {
        Debugger.i(TAG, "---onDestroy---start");
        if (MupdfMacro.isSharing) {
            EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_exit_annotation).objects(mediaId).build());
        }
        ActUtil.removeActivity(this);
        unregisterEventBus();
        MupdfMacro.isSharing = false;
        MupdfMacro.launchSrcmemid = 0;
        MupdfMacro.launchSrcwbid = 0;
        MupdfMacro.sharingIds.clear();
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler = null;
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                @Override
                public void applyToView(View view) {
                    Debugger.i(TAG, "---onDestroy---PageView");
                    ((PageView) view).releaseBitmaps();
                }
            });
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

