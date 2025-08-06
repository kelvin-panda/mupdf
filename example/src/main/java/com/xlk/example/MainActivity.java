package com.xlk.example;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.artifex.mupdf.viewer.DocumentActivity;
import com.xlk.mupdf.library.MuPdfDocumentActivity;
import com.xlk.mupdf.library.MupdfConfig;
import com.xlk.mupdf.library.MupdfMacro;

public class MainActivity extends AppCompatActivity {
    public String root_dir;//应用管理清除数据会删除

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MupdfMacro.isHengXunVersion = false;
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void openPdfFile(View view) {
        uploadFile.launch("*/*");
    }

    private final ActivityResultLauncher<String> uploadFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            result -> {
                if (result == null) return;
                openMupdf(result);
            });

    private void openMupdf(Uri uri) {
        MupdfConfig mupdfConfig = new MupdfConfig.Builder()
                .fileUri(uri.toString())
                .watermarkEnable(true)
                .watermarkContent("测试会议-张大龙")
//                .watermarkColor(Color.YELLOW)
                .build();
        MuPdfDocumentActivity.jump(this, mupdfConfig);
//        DocumentActivity.jump(this,uri);
    }
}