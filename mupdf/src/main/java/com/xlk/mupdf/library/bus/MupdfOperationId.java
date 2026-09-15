package com.xlk.mupdf.library.bus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成微秒级且当前进程内单调递增的操作标识。
 */
public final class MupdfOperationId {
    private static final AtomicLong LAST_ID = new AtomicLong();

    private MupdfOperationId() {
    }

    public static long next() {
        long nextId = System.currentTimeMillis() * 1000L;
        while (true) {
            long lastId = LAST_ID.get();
            if (nextId <= lastId) {
                nextId = lastId + 1;
            }
            if (LAST_ID.compareAndSet(lastId, nextId)) {
                return nextId;
            }
        }
    }
}
