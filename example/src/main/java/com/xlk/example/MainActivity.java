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
import com.xlk.mupdf.library.MupdfMacro;
import com.xlk.mupdf.library.bus.MupdfBusType;
import com.xlk.mupdf.library.bus.MupdfEventMessage;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import me.jessyan.autosize.internal.CancelAdapt;

public class MainActivity extends AppCompatActivity implements CancelAdapt {
    private static final String TAG = "MainActivity";
    private int mode = MupdfClarityMode.UNRESTRICTED;
    private ActivityMainBinding bd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bd = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(bd.getRoot());
        EventBus.getDefault().register(this);
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void eventBus(MupdfEventMessage msg) {
        switch (msg.getType()) {
            case MupdfBusType.out_open_inform: {
                Object[] objects = msg.getObjects();
                String filePath = (String) objects[0];
                if (filePath.isEmpty()) {
                    String uri = (String) objects[1];
                    Log.d(TAG, "eventBus: uri=" + uri);
                    WpsUtil.openWps(this, Uri.parse(uri), true);
                } else {
                    String destPath = getExternalCacheDir() + File.separator + FileUtils.getFileName(filePath);
                    boolean copy = FileUtils.copy(filePath, destPath);
                    Log.d(TAG, "eventBus: filePath=" + filePath + ",destPath=" + destPath + ",copy=" + copy);
                    WpsUtil.openFile(this, destPath);
                }
                break;
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
        MupdfMacro.delete_history_annotation = bd.cbDeleteAnnotation.isChecked();//清理批注
        MupdfMacro.isHengXunVersion = bd.cbHengxun.isChecked();
        Log.d(TAG, "mode=" + mode);
        MupdfConfig mupdfConfig = new MupdfConfig.Builder()
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
                .informSignature(bd.cbInformSignature.isChecked())//通知签名（秘书端使用）
                //界面水印
                .build();
        MuPdfDocumentActivity.jump(this, mupdfConfig);
    }
}