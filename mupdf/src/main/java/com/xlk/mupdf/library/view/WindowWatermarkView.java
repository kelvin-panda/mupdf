package com.xlk.mupdf.library.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class WindowWatermarkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private String text = "";
    private int textColor = 0x33FFAB00;
    private float textSizePx;
    private float angle = -25f;

    public WindowWatermarkView(Context context) {
        super(context);
        init();
    }

    public WindowWatermarkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WindowWatermarkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textSizePx = sp(22);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    public void setWatermark(String text, int color) {
        this.text = text == null ? "" : text;
        this.textColor = color;
        invalidate();
    }

    public void setWatermarkAngle(float angle) {
        this.angle = angle;
        invalidate();
    }

    public void setWatermarkTextSizeSp(float textSizeSp) {
        this.textSizePx = sp(textSizeSp);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (TextUtils.isEmpty(text)) return;

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        paint.setColor(textColor);
        paint.setTextSize(textSizePx);

        float textWidth = paint.measureText(text);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float stepX = Math.max(textWidth + dp(96), dp(180));
        float stepY = Math.max(textHeight + dp(96), dp(150));

        canvas.save();
        canvas.rotate(angle, width / 2f, height / 2f);
        float diagonal = (float) Math.hypot(width, height);
        float startX = -diagonal;
        float endX = width + diagonal;
        float startY = -diagonal;
        float endY = height + diagonal;
        for (float y = startY; y <= endY; y += stepY) {
            for (float x = startX; x <= endX; x += stepX) {
                canvas.drawText(text, x, y, paint);
            }
        }
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
