package com.xlk.mupdf.library;
import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author : Administrator
 * created on 2025/12/24 15:53
 */

// 定义注解
@IntDef({
        MupdfClarityMode.LIMIT_8K,
        MupdfClarityMode.LIMIT_4K,
        MupdfClarityMode.LIMIT_2K,
        MupdfClarityMode.LIMIT_1080P,
        MupdfClarityMode.LIMIT_720P,
        MupdfClarityMode.UNRESTRICTED
})
@Retention(RetentionPolicy.SOURCE)
public @interface MupdfClarityMode {
    int LIMIT_8K = 0;
    int LIMIT_4K = 1;
    int LIMIT_2K = 2;
    int LIMIT_1080P = 3;
    int LIMIT_720P = 4;
    int UNRESTRICTED = -1;
}
