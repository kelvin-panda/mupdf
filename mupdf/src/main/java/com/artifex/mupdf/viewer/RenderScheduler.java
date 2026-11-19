package com.artifex.mupdf.viewer;

import com.artifex.mupdf.util.Debugger;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 页渲染调度器：**单线程** + 两级优先级。
 *
 * <p>为什么要单线程：MuPDF 的页面 / 显示列表对象不能跨线程使用，页显示列表缓存也按
 * "创建线程"归属。固定一条线程渲染，{@code dl=hit} 才能稳定命中（省掉 loadPage +
 * toDisplayList，实测 40~140ms/页），缩略图建好的显示列表也能被后续打开该页复用。</p>
 *
 * <p>为什么要优先级：所有渲染共用一个线程时，缓冲页/缩略图的渲染任务会排在"用户正等着的
 * 可见页"前面。给任务分两级后，可见页、补高清、批注刷新走 {@code high}，
 * 缓冲页预览、缩略图走 {@code low}；高优先级永远先跑，排队中的低优先级任务
 * 还能在页面离开窗口时被取消（真正从队列里失效，不占用渲染时间）。</p>
 *
 * <p>注意：渲染开始后不可中断（{@code doCancel()} 故意不 abort，避免 native 栈残留 SIGSEGV），
 * 所以"取消"只对**排队中**的任务有效。</p>
 */
public final class RenderScheduler {

    private static final String TAG = "RenderScheduler";
    private static final RenderScheduler INSTANCE = new RenderScheduler();

    public static RenderScheduler get() {
        return INSTANCE;
    }

    /** 一次提交的任务；{@link #cancel()} 只对尚未开始执行的任务有效 */
    public static final class Task implements Runnable {
        private final Runnable body;
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile boolean cancelled;

        private Task(Runnable body) {
            this.body = body;
        }

        @Override
        public void run() {
            try {
                if (!cancelled) {
                    body.run();
                }
            } catch (Throwable t) {
                Debugger.e(TAG, t);
            } finally {
                done.countDown();
            }
        }

        /** 排队中取消；已在执行时无效果（渲染不可中断） */
        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        /** 等这次任务执行完（已在执行/已完成都会很快返回） */
        public boolean await(long timeoutMs) {
            try {
                return done.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private final Object lock = new Object();
    private final ArrayDeque<Task> highQueue = new ArrayDeque<>();
    private final ArrayDeque<Task> lowQueue = new ArrayDeque<>();
    /** 当前正在执行的任务（用于"是否忙"判断与调试） */
    private volatile Task runningTask;

    private RenderScheduler() {
        Thread worker = new Thread(this::loop, "mupdf-render");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        while (true) {
            Task task;
            synchronized (lock) {
                while (highQueue.isEmpty() && lowQueue.isEmpty()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                task = highQueue.pollFirst();
                if (task == null) {
                    task = lowQueue.pollFirst();
                }
            }
            if (task != null) {
                runningTask = task;
                try {
                    task.run();
                } finally {
                    runningTask = null;
                }
            }
        }
    }

    /**
     * 提交渲染/绘制任务。
     *
     * @param highPriority true = 用户正等着的（可见页首帧、补高清、批注刷新、静默换尺寸）；
     *                     false = 可让路的（缓冲页预览、缩略图）
     */
    public Task submit(Runnable body, boolean highPriority) {
        Task task = new Task(body);
        synchronized (lock) {
            if (highPriority) {
                highQueue.addLast(task);
            } else {
                lowQueue.addLast(task);
            }
            lock.notifyAll();
        }
        return task;
    }

    public boolean isBusy() {
        return runningTask != null;
    }

    /** 排队中的任务数（不含正在执行的） */
    public int pendingCount() {
        synchronized (lock) {
            return highQueue.size() + lowQueue.size();
        }
    }
}
