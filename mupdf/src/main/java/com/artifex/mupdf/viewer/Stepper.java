package com.artifex.mupdf.viewer;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;

import com.artifex.mupdf.util.Debugger;

public class Stepper {
    private static final String TAG = "Stepper";
    protected final View mPoster;
    protected final Runnable mTask;
    protected boolean mPending;

    public Stepper(View v, Runnable r) {
        mPoster = v;
        mTask = r;
        mPending = false;
    }

    @SuppressLint("NewApi")
    public void prod() {
//        try {
//            throw new Exception("prod 通知调用ReaderView中的run方法 mPending=" + mPending);
//        } catch (Exception e) {
//            Debugger.e(e);
//        }
        if (!mPending) {
            mPending = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mPoster.postOnAnimation(new Runnable() {
                    @Override
                    public void run() {
                        mPending = false;
                        mTask.run();
                    }
                });
            } else {
                mPoster.post(new Runnable() {
                    @Override
                    public void run() {
                        mPending = false;
                        mTask.run();
                    }
                });

            }
        }
    }
}
