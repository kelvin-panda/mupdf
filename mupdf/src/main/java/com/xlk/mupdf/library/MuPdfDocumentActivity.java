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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
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

    //<editor-fold desc="成员变量">

    private View viewTopAnnotation, viewTopSignature, viewTopScreenshot, viewTopRefresh, viewTopJump, viewTopSave, viewTopBookmark, viewTopClose,//顶部控件
            viewArtClose, viewArtPen, viewArtLine, viewArtBrush, viewArtColor, viewArtHighlight, viewArtRevoke, viewArtDone,//画板控件
            viewArtInvite, viewArtUnderline, viewArtStrikeout, viewArtFreeText,
            viewTopWatermark, viewTopSignTable, viewTopSignRow, viewTopSearch;
    private String srcFilePath, annotationSavePath, srcUri, mWatermark;
    private int mWatermarkColor;
    private int mediaId;
    private boolean uploadEnable, annotationEnable, signatureEnable, captureEnable, wpsOpenEnable,
            mAnnotationVisible, mInkSizeViewVisible, afterAnnotationRefresh;
    private AnnotationArtBoard artBoard;
    private TextView viewArtSizeTv;
    private SeekBar viewArtSeekBar;
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
    enum TopBarMode {Main, Search, More}

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
    private ImageButton mSearchBack, mSearchFwd, mSearchClose;
    private EditText mSearchText;
    private SearchTask mSearchTask;
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
    private int signTableTotalNames = 0;

    private Runnable pendingPageUpdate;

    private void schedulePageUpdate(Runnable update) {
        if (pendingPageUpdate != null) {
            mainHandler.removeCallbacks(pendingPageUpdate);
        }
        pendingPageUpdate = update;
        mainHandler.postDelayed(update, 50);
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
        AutoSizeCompat.cancelAdapt(superResources);
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

    long lastClickTime = 0L;

    private void extracted(Bundle savedInstanceState) {
        makeButtonsView();

        // 文本查找：搜索栏切换与搜索按钮
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

        if (MupdfMacro.shareAnnotationEnable) {
            //有文件id才显示
            viewArtInvite.setVisibility(mediaId != 0 ? View.VISIBLE : View.GONE);
        }
        //文件名称
        mDocNameView.setText(mDocTitle);

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
        //退出文档批注上传开关
        viewTopSave.setOnClickListener(v -> {
            saveWhenExit = !saveWhenExit;
            toast(saveWhenExit ? getString(R.string.save_to_annotation_directory_upon_exit) : getString(R.string.cancel_exit_and_save_to_annotation_directory));
        });
        viewTopSave.setVisibility(uploadEnable ? View.VISIBLE : View.GONE);
        //内容流水印
        viewTopWatermark.setOnClickListener(v -> {
            showWatermarkDialog();
        });
        viewTopWatermark.setVisibility(uploadEnable ? View.VISIBLE : View.GONE);
        //签名表格
        if (viewTopSignTable != null) {
            viewTopSignTable.setOnClickListener(v -> {
                showSignTableDialog();
            });
            viewTopSignTable.setVisibility(signatureEnable ? View.VISIBLE : View.GONE);
        }
        //填写签名
        if (viewTopSignRow != null) {
            viewTopSignRow.setOnClickListener(v -> {
                showSignRowDialog();
            });
            viewTopSignRow.setVisibility(signatureEnable ? View.VISIBLE : View.GONE);
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
        viewTopSignature.setVisibility(signatureEnable ? View.VISIBLE : View.GONE);
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
            mDocView.restorePosition();
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
        viewTopJump.setVisibility(wpsOpenEnable ? View.VISIBLE : View.GONE);
        //截图批注
        viewTopScreenshot.setOnClickListener(v -> {
            hideButtons();
            mainHandler.postDelayed(() -> {
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_screenshot).objects(mDocTitle, 0).build());
            }, 250);
        });
        viewTopScreenshot.setVisibility(captureEnable ? View.VISIBLE : View.GONE);
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
                            Point[] percentPoints;

                            percentPoints = core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);

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

                        mDocView.restorePosition();

//                        core.logAnnotations(mDocView.mCurrent);
                    }
                }
            });
            artBoard.setPaintWidth(default_ink_size);
            artBoard.setFreeTextListener(pos -> {
                showFreeTextDialog(pos);
            });
            artBoard.setTextMarkupListener((type, start, end, color, strokeWidth) -> {
                PageView pv = (PageView) mDocView.getDisplayedView();
                if (pv == null || core == null) return;
                int w = pv.getWidth();
                int h = pv.getHeight();
                if (w <= 0 || h <= 0) return;
                float[] c = core.parseColor(color);
                Object result = core.addTextMarkupAnnotation(mDocView.mCurrent, w, h,
                        type, start, end, c, mDisplayDPI, mDisplayDPI);
                if (result != null) {
                    hadAnnotation = true;
                    schedulePageUpdate(() -> pv.update());
                }
            });
            pageView.addView(artBoard);
            artBoard.layout(0, 0, width, height);
        });
        viewTopAnnotation.setVisibility(annotationEnable ? View.VISIBLE : View.GONE);

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
            hideAnnotationViews();
        });
        //取消批注
        viewArtClose.setOnClickListener(v -> {
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

    private void hideAnnotationViews() {
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

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        currentPageIndex = index;
        if (mPageNumberView != null)
            mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", index + 1, core.countPages()));
    }

    private void makeButtonsView() {
        int layoutResId = MupdfMacro.isHengXunVersion ? R.layout.mupdf_document_activity_hx : R.layout.mupdf_document_activity;
        mButtonsView = getLayoutInflater().inflate(layoutResId, null);
        rootView = (RelativeLayout) mButtonsView.findViewById(R.id.rootView);
        mTvMark = (TextView) mButtonsView.findViewById(R.id.tv_mark);
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
        viewTopSave = mButtonsView.findViewById(R.id.viewTopSave);
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

        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mLlPageView.setVisibility(View.INVISIBLE);

        if (isOnlyPreview) {
            viewTopScreenshot.setVisibility(View.GONE);
            viewTopSave.setVisibility(View.GONE);
            viewTopSignature.setVisibility(View.GONE);
            viewTopAnnotation.setVisibility(View.GONE);
            viewTopWatermark.setVisibility(View.GONE);
            if (viewTopSignTable != null) viewTopSignTable.setVisibility(View.GONE);
            if (viewTopSearch != null) viewTopSearch.setVisibility(View.GONE);
        }
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
                .setTitle(getString(R.string.title_save_pop))
                .setPositiveButton(getString(R.string.save_and_exit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        saveAndExit();
                    }
                })
                .setNegativeButton(getString(R.string.exit_directly), new DialogInterface.OnClickListener() {
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
        progressDialog.setMessage(getString(R.string.please_wait));
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
                PageView pageView = (PageView) mDocView.getDisplayedView();
                if (pageView == null) return;
                int width = pageView.getWidth();
                int height = pageView.getHeight();
                float[] color = core.parseColor(artBoard != null ? artBoard.getPaintColor() : Color.RED);
                core.addFreeTextAnnotation(mDocView.mCurrent, width, height,
                        position, text, "Helv", 16f, color);
                hadAnnotation = true;
                schedulePageUpdate(() -> pageView.update());
            }
            dialog.dismiss();
        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.mupdf_cancel),
                (dialog, which) -> dialog.dismiss());
        alert.show();
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
                mDocView.afterAnnotation();
                mDocView.setDisplayedViewIndex(mDocView.mCurrent);
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
                    mDocView.afterAnnotation();
                    mDocView.setDisplayedViewIndex(core.countPages() - 1);
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
                            mDocView.afterAnnotation();
                            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
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

