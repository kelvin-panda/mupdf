package com.xlk.mupdf.library.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.xlk.mupdf.library.R;


/**
 * author : Administrator
 * date : 2022/7/4 10:40
 * description :
 */
public class MupdfColorPickerDialog extends Dialog {
    private static final String TAG = "MupdfColorPickerDialog";
    private MupdfColorPickerView.OnColorSubmitListener mListener;
    private final int defaultColor;

    public MupdfColorPickerDialog(@NonNull Context context, MupdfColorPickerView.OnColorSubmitListener listener, int color) {
        super(context);
        mListener = listener;
        defaultColor = color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        setContentView(R.layout.mupdf_color_picker_layout);
        MupdfColorPickerView colorPickerView = findViewById(R.id.colorPickerView);
        EditText edtR = findViewById(R.id.edtR);
        EditText edtG = findViewById(R.id.edtG);
        EditText edtB = findViewById(R.id.edtB);
        EditText edtA = findViewById(R.id.edtA);
        EditText edtHex = findViewById(R.id.edtHex);

        edtHex.setKeyListener(null);

        colorPickerView.setColorChangedListener(new MupdfColorPickerView.OnColorChangedListener() {
            @Override
            public void colorChanged(int color) {
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                int alpha = Color.alpha(color);
                edtR.setText(String.valueOf(red));
                edtG.setText(String.valueOf(green));
                edtB.setText(String.valueOf(blue));
                edtA.setText(String.valueOf(alpha));
                edtHex.setText(Integer.toHexString(color).toUpperCase());
            }
        });
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String r = edtR.getText().toString();
                String g = edtG.getText().toString();
                String b = edtB.getText().toString();
                String a = edtA.getText().toString();
                int red = format2Int(r);
                int green = format2Int(g);
                int blue = format2Int(b);
                int alpha = format2Int(a);
                int color = Color.argb(alpha, red, green, blue);
                colorPickerView.setCurrentColor(color);
                edtHex.setText(Integer.toHexString(color).toUpperCase());
            }
        };
        InputFilter[] filters = {new RangeInputFilter(0, 255)};
        edtR.setFilters(filters);
        edtG.setFilters(filters);
        edtB.setFilters(filters);
        edtA.setFilters(filters);

        edtR.addTextChangedListener(textWatcher);
        edtG.addTextChangedListener(textWatcher);
        edtB.addTextChangedListener(textWatcher);
        edtA.addTextChangedListener(textWatcher);

        int red = Color.red(defaultColor);
        int green = Color.green(defaultColor);
        int blue = Color.blue(defaultColor);
        int alpha = Color.alpha(defaultColor);
        edtR.setText(String.valueOf(red));
        edtG.setText(String.valueOf(green));
        edtB.setText(String.valueOf(blue));
        edtA.setText(String.valueOf(alpha));

        findViewById(R.id.submit).setOnClickListener(v -> {
            mListener.submitColor(colorPickerView.getCurrentColor());
            dismiss();
        });
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, "onDetachedFromWindow: ");
        mListener = null;
    }

    private int format2Int(String color) {
        try {
            return Integer.parseInt(color);
        } catch (Exception e) {
            return 0;
        }
    }

    public static class RangeInputFilter implements InputFilter {
        private final int mMinimum;//限制最小值
        private final int mMaximum;//限制最大值

        public RangeInputFilter(int minimum, int maximum) {
            mMinimum = minimum;
            mMaximum = maximum;
        }

        /**
         * @param source 新输入的字符序列
         * @param start  新输入的字符序列的起始位置
         * @param end    新输入的字符序列的结束位置
         * @param dest   当前已存在的字符序列，即EditText中已有的内容
         * @param dstart 当前字符序列中将被替换或插入的起始位置
         * @param dend   当前字符序列中将被替换或插入的结束位置
         * @return <ul>
         * <li>如果返回 null，表示不对输入进行过滤，使用原始输入。</li>
         * <li>返回空字符串 "" 表示阻止输入。</li>
         * <li>返回的其他字符序列将替代原始输入。</li>
         * </ul>
         */
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                // 构建新输入后的字符串
                String newVal = dest.toString().substring(0, dstart) + source.toString() + dest.toString().substring(dend);
                int input = Integer.parseInt(newVal);
                if (isInRange(mMinimum, mMaximum, input)) {
                    return null;
                }
            } catch (NumberFormatException nfe) {
            }
            return "";
        }


        private boolean isInRange(int mMinimum, int mMaximum, int input) {
            return input >= mMinimum && input <= mMaximum;
        }
    }

}
