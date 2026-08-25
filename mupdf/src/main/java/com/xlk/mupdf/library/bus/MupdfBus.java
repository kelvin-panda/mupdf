package com.xlk.mupdf.library.bus;

import org.greenrobot.eventbus.EventBus;

/**
 * @author : Administrator
 * created on 2026/8/24 16:04
 */
public class MupdfBus {
    public static void post(String type, Object... values) {
        post(new MupdfEventMessage.Builder()
                .type(type)
                .objects(values)
                .build());
    }

    public static void post(MupdfEventMessage message) {
        EventBus.getDefault().post(message);
    }
}
