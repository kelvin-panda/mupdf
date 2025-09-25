package com.artifex.mupdf.util;

import android.app.Activity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author : Administrator
 * created on 2025/6/24 16:29
 */
public class ActUtil {
    private static final String TAG = "ActUtil";
    private static final List<Activity> activities = new ArrayList<>();

    public static void addActivity(Activity activity) {
        activities.add(activity);
        Debugger.e(TAG, "addActivity");
    }

    public static void removeActivity(Activity activity) {
        activities.remove(activity);
        Debugger.e(TAG, "removeActivity");
    }

    public static void finishActivity(Class<?> activityClass) {
        Iterator<Activity> iterator = activities.iterator();
        while (iterator.hasNext()) {
            Activity activity = iterator.next();
            if (activity.getClass().equals(activityClass)) {
                Debugger.e(TAG, "结束旧的MuPdfDocumentActivity");
                activity.finish();
                iterator.remove();
                break;
            }
        }
    }
}
