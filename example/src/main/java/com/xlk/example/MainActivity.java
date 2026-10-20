package com.xlk.example;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.blankj.utilcode.util.FileUtils;
import com.xlk.example.databinding.ActivityMainBinding;
import com.xlk.mupdf.library.MuPdfDocumentActivity;
import com.xlk.mupdf.library.MupdfClarityMode;
import com.xlk.mupdf.library.MupdfConfig;
import com.xlk.mupdf.library.api.MupdfHost;
import com.xlk.mupdf.library.api.MupdfSession;

import java.io.File;
import java.util.List;

import me.jessyan.autosize.internal.CancelAdapt;

/**
 * 示例：7.0.0 起通过 {@link MupdfHost} 接收库的回调，不再使用 EventBus。
 */
public class MainActivity extends AppCompatActivity implements CancelAdapt, MupdfHost {
    private static final String TAG = "MainActivity";
    private int mode = MupdfClarityMode.UNRESTRICTED;
    private ActivityMainBinding bd;
    /**
     * 当前会话句柄。库在 onSessionReady 时给出，onSessionClosed 时失效。
     */
    private MupdfSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bd = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(bd.getRoot());
        applyPermission();
        RadioGroup radioGroup = findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i) {
                    case R.id.rb1:
                        mode = MupdfClarityMode.UNRESTRICTED;
                        break;
                    case R.id.rb8k:
                        mode = MupdfClarityMode.LIMIT_8K;
                        break;
                    case R.id.rb4k:
                        mode = MupdfClarityMode.LIMIT_4K;
                        break;
                    case R.id.rb2k:
                        mode = MupdfClarityMode.LIMIT_2K;
                        break;
                    case R.id.rb1080p:
                        mode = MupdfClarityMode.LIMIT_1080P;
                        break;
                    case R.id.rb720p:
                        mode = MupdfClarityMode.LIMIT_720P;
                        break;
                }
                Log.d(TAG, "MainActivity.onCheckedChanged: mode=" + mode);
            }
        });
    }

    //<editor-fold desc="MupdfHost（库 → 宿主）">

    @Override
    public void onSessionReady(MupdfSession session) {
        this.session = session;
        Log.d(TAG, "onSessionReady: id=" + session.id() + ", mediaId=" + session.mediaId());
    }

    @Override
    public void onSessionClosed(String sessionId, int reason) {
        this.session = null;
        Log.d(TAG, "onSessionClosed: id=" + sessionId + ", reason=" + reason);
    }

    /**
     * 用户选择外部应用打开（原 EventBus 的 {@code out_open_inform}）。
     */
    @Override
    public void onOpenExternally(String filePath, String uri) {
        Log.d(TAG, "onOpenExternally: filePath=" + filePath + ", uri=" + uri);
        if (filePath == null || filePath.isEmpty()) {
            Log.d(TAG, "onOpenExternally: uri=" + uri);
            WpsUtil.openWps(this, Uri.parse(uri), true);
        } else {
            String destPath = getExternalCacheDir() + File.separator + FileUtils.getFileName(filePath);
            boolean copy = FileUtils.copy(filePath, destPath);
            Log.d(TAG, "onOpenExternally: filePath=" + filePath + ",destPath=" + destPath + ",copy=" + copy);
            WpsUtil.openFile(this, destPath);
        }
    }

    @Override
    public void onScreenshotRequested(String docTitle) {
        Log.d(TAG, "onScreenshotRequested: docTitle=" + docTitle);
    }

    @Override
    public void onUploadRequested(String savedPath, int uploadDirId) {
        Log.d(TAG, "onUploadRequested: savedPath=" + savedPath + ", dirId=" + uploadDirId);
    }

    @Override
    public void onLocalOperations(List<com.xlk.mupdf.library.api.MupdfInkBean> operations) {
        Log.d(TAG, "onLocalOperations: size=" + (operations == null ? 0 : operations.size()));
    }

    //</editor-fold>

    private final ActivityResultLauncher<Intent> applyPermission = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();
        if (Activity.RESULT_OK == resultCode) {
        } else {
            applyPermission();
        }
    });

    private void applyPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                //此手机是Android 11或更高的版本，且没有访问所有文件权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                applyPermission.launch(intent);
            }
        }
    }

    public void openPdfFile(View view) {
        pdfFileLauncher.launch("application/pdf");
    }

    private final ActivityResultLauncher<String> pdfFileLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            result -> {
                if (result == null) return;
                openMupdf(result);
            });

    private void openMupdf(Uri uri) {
        Log.d(TAG, "mode=" + mode);
        MupdfConfig mupdfConfig = new MupdfConfig.Builder()
                // sessionId 是并存会话的路由键；Activity 重建后保持不变
                .sessionId("example-" + System.currentTimeMillis())
                // 注入能力端口，7.0.0 起必填（为 null 时 jump() 会抛 IllegalStateException）
                .host(this)
                .fileUri(uri.toString())
                .clarityLimitMode(mode)                             //清晰度
                .fullScreenEnable(bd.cbFull.isChecked())            //全屏
                .annotationEnable(bd.cbAnnotation.isChecked())      //批注
                .signatureEnable(bd.cbSignature.isChecked())        //签名
                .captureEnable(bd.cbCapture.isChecked())            //截图
                .wpsOpenEnable(bd.cbWps.isChecked())                //wps打开
                .watermarkEnable(bd.cbWatermark.isChecked())        //水印
                .watermarkContent("保密文件限制外露")
                .watermarkColor(Color.parseColor("#66FFAB00"))
                .signatureForm(bd.cbSignatureForm.isChecked())      //签名表
                .fillInSignature(bd.cbSignatureTableSignature.isChecked()) //签名表签名
                .annotationInputText(bd.cbText.isChecked())         //文本
                //共享
                .windowWatermarkEnable(bd.cbPdfWatermark.isChecked()) //界面水印
                .backButtonEnabled(bd.cbBackButton.isChecked())
                .informSignature(bd.cbInformSignature.isChecked())  //通知签名（秘书端使用）
                .shouldSignature(bd.cbShouldSignature.isChecked())  //打开后提交签名（参会端使用）
                //界面水印
                // 7.0.0：原 MupdfMacro.isHengXunVersion 静态开关迁到这里
                .hengXunStyle(bd.cbHengxun.isChecked())
                .build();
        MuPdfDocumentActivity.jump(this, mupdfConfig);
    }
}
