package com.xlk.mupdf.library.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * author : Administrator
 * date : 2022/7/4 10:40
 * description :
 */
public class ColorPickerDialog extends Dialog {
    public ColorPickerDialog(@NonNull Context context, OnColorChangedListener listener, int color) {
        super(context);
        OnColorChangedListener l = new OnColorChangedListener() {
            @Override
            public void colorChanged(int color) {
                listener.colorChanged(color);
                dismiss();
            }
        };
        setContentView(new ColorPickerView(context, l, color));
    }

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    public static class ColorPickerView extends View {
        private Paint defaultPaint;
        private Paint mGradientPaint;
        private Paint mLinearPaint;
        private Paint mCenterPaint;
        private Paint mRadialPaint;
        private OnColorChangedListener mListener;
        private boolean mTrackingCenter;
        private boolean mHighlightCenter;
        private boolean mTrackingLinGradient;
        private int[] mRadialColors = new int[]{
                -0x10000, -0xff01, -0xffff01,
                -0xff0001, -0xff0100, -0x100, -0x10000};
        private int[] mLinearColors;
        private final float PI = 3.1415926f;
        private int magnification = 2;
        private float centerX = 100f * magnification;
        private float centerY = 100f * magnification;
        private float defaultViewWidth = centerX * 2;
        private float defaultViewHeight = centerY * 2;
        private float defaultLinearHeight = 40f * magnification;
        private float CENTER_RADIUS = 32f * magnification;

        public ColorPickerView(Context context, OnColorChangedListener listener, int color) {
            super(context);
            mListener = listener;
            mLinearColors = getColors(color);
            SweepGradient sweepGradient = new SweepGradient(0f, 0f, mRadialColors, null);
            mGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mGradientPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mGradientPaint.setShader(sweepGradient);
            mGradientPaint.setStrokeWidth(1f);

            LinearGradient linearShader =
                    new LinearGradient(
                            0f,
                            0f,
                            defaultViewWidth,
                            0f,
                            mLinearColors,
                            null,
                            Shader.TileMode.CLAMP
                    );
            mLinearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mLinearPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mLinearPaint.setShader(linearShader);
            mLinearPaint.setStrokeWidth(1f);

            mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mCenterPaint.setColor(color);
            mCenterPaint.setStrokeWidth(3f * magnification);

            mRadialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mRadialPaint.setColor(Color.BLACK);
            mRadialPaint.setStrokeWidth(3f * magnification);

            defaultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            defaultPaint.setColor(Color.WHITE);
            defaultPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            defaultPaint.setStrokeWidth(1f);
        }

        private int[] getColors(int color) {
            if (color == Color.BLACK || color == Color.WHITE) {
                return new int[]{Color.BLACK, Color.WHITE};
            }
            return new int[]{Color.BLACK, color, Color.WHITE};
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension((int) defaultViewWidth, (int) (defaultViewHeight + defaultLinearHeight));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float min = Math.min(viewWidth, viewHeight);
            float r = centerX;
            canvas.translate(centerX, centerY);
            canvas.drawOval(new RectF(-r, -r, r, r), mGradientPaint);
            canvas.drawCircle(0f, 0f, CENTER_RADIUS + mCenterPaint.getStrokeWidth(), defaultPaint);
            if (mTrackingCenter) {
                int color = mCenterPaint.getColor();
                mCenterPaint.setStyle(Paint.Style.STROKE);
                if (mHighlightCenter) {
                    mCenterPaint.setAlpha(0xff);
                } else {
                    mCenterPaint.setAlpha(0x80);
                }
                canvas.drawCircle(0f, 0f, CENTER_RADIUS + mCenterPaint.getStrokeWidth(), mCenterPaint);
                mCenterPaint.setStyle(Paint.Style.FILL);
                mCenterPaint.setColor(color);
            }
            canvas.drawCircle(0f, 0f, CENTER_RADIUS, mCenterPaint);
            canvas.translate(-centerX, -centerY);
            canvas.drawRect(10f, min + 10f + mLinearPaint.getStrokeWidth() / 2, viewWidth - 10f, viewHeight - 10f, mLinearPaint);
        }

        private int interpColor(int[] colors, float unit) {
            if (unit <= 0) return colors[0];
            if (unit >= 1) return colors[colors.length - 1];
            float p = unit * (colors.length - 1);
            int i = (int) p;
            p -= i;
            int c0 = colors[i];
            int c1 = colors[i + 1];
            int a = ave(Color.alpha(c0), Color.alpha(c1), p);
            int r = ave(Color.red(c0), Color.red(c1), p);
            int g = ave(Color.green(c0), Color.green(c1), p);
            int b = ave(Color.blue(c0), Color.blue(c1), p);
            return Color.argb(a, r, g, b);
        }

        private int ave(int s, int d, float p) {
            return s + Math.round((p * (d - s)));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - centerX;
            float y = event.getY() - centerY;
            boolean inCenter = Math.sqrt(x * x + y * y) <= CENTER_RADIUS;
            boolean outOfRadialGradient = y > centerY;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    mTrackingCenter = inCenter;
                    mTrackingLinGradient = outOfRadialGradient;
                    if (inCenter) {
                        mHighlightCenter = true;
                        invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mTrackingCenter) {
                        if (mHighlightCenter != inCenter) {
                            mHighlightCenter = inCenter;
                            invalidate();
                        }
                    } else if (mTrackingLinGradient) {
                        float unit = (Math.max(
                                0f, Math.min((centerX * 2), x + centerX)
                        ) / (centerX * 2));
                        mCenterPaint.setColor(interpColor(mLinearColors, unit));
                        invalidate();
                    } else {
                        float angle = (float) Math.atan2(y, x);
                        float unit = angle / (2 * PI);
                        if (unit < 0) unit += 1;
                        int color = interpColor(mRadialColors, unit);
                        mCenterPaint.setColor(color);
                        mRadialPaint.setColor(color);
                        invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    if (mTrackingCenter) {
                        if (inCenter) {
                            mListener.colorChanged(mCenterPaint.getColor());
                        }
                        mTrackingCenter = false;
                        invalidate();
                    }
                    break;
                }
            }
            return true;
        }
    }
}
