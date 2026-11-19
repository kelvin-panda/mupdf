package com.artifex.mupdf.viewer;

import android.os.Handler;
import android.os.Looper;

import com.artifex.mupdf.util.Debugger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可取消的后台任务（原 AsyncTask 实现换成 {@link RenderScheduler}）。
 *
 * <p>换掉 AsyncTask 的原因：AsyncTask 内部用 {@code FutureTask} 提交，无法塞进
 * 优先级队列（FutureTask 不可比较），所以"可见页插队、缓冲页让路"就实现不了。
 * 这里改成：提交给 {@link RenderScheduler}（单线程 + 两级优先级），
 * 完成后由主线程回调 {@link #onPostExecute}，语义与原实现保持一致：</p>
 *
 * <ul>
 *   <li>{@link #onPreExecute()} 在调用方线程（通常是主线程）同步执行；</li>
 *   <li>{@link #onPostExecute} 在主线程执行，之后必定执行一次 {@code doCleanup()}；</li>
 *   <li>{@link #cancel()} 对**排队中**的任务立即生效（不再执行），对**已在执行**的任务只调用
 *       {@code doCancel()}（渲染不可中断），**不阻塞调用线程**；位图所有权由 PageView 提前摘下，\n *       任务结束后由 {@code doCleanup()} 回收。</li>
 * </ul>
 */
public class CancellableAsyncTask<Params, Result> {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final CancellableTaskDefinition<Params, Result> ourTask;
    private final boolean highPriority;
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false);

    private RenderScheduler.Task queuedTask;
    private volatile boolean cancelled;
    private volatile boolean started;

    public CancellableAsyncTask(CancellableTaskDefinition<Params, Result> task) {
        this(task, true);
    }

    /**
     * @param highPriority true = 用户正等着的渲染（可见页/补高清/批注刷新），排队时插队；
     *                     false = 可让路的（缓冲页预览、缩略图）
     */
    public CancellableAsyncTask(CancellableTaskDefinition<Params, Result> task, boolean highPriority) {
        if (task == null) {
            throw new IllegalArgumentException();
        }
        this.ourTask = task;
        this.highPriority = highPriority;
    }

    public void onPreExecute() {
    }

    public void onPostExecute(Result result) {
    }

    public void execute(final Params... params) {
        onPreExecute();
        queuedTask = RenderScheduler.get().submit(new Runnable() {
            @Override
            public void run() {
                if (cancelled) {
                    // 排队中被取消：不再渲染，但清理必须补一次（endPageRender 等计数在这里归还）
                    postCleanup();
                    return;
                }
                started = true;
                Result result = null;
                try {
                    result = ourTask.doInBackground(params);
                } catch (Throwable t) {
                    Debugger.e("CancellableAsyncTask", t);
                } finally {
                }
                final Result finalResult = result;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!cancelled) {
                            onPostExecute(finalResult);
                        }
                        cleanupOnce();
                    }
                });
            }
        }, highPriority);
    }

    public void cancel() {
        cancelled = true;
        if (queuedTask != null) {
            queuedTask.cancel();
        }
        ourTask.doCancel();
        // 刻意**不**在这里等渲染结束：cancel() 由 PageView 在主线程调用（回收子 View 时），
        // 而单页渲染可能还要 4~7s，等下去就是看门狗/ANR。
        // 位图所有权已由 PageView 在 cancel 前摘走，任务结束后的 doCleanup 负责回收。
        postCleanup();
    }

    private void postCleanup() {
        if (cleanedUp.get()) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cleanupOnce();
            }
        });
    }

    private void cleanupOnce() {
        if (cleanedUp.compareAndSet(false, true)) {
            ourTask.doCleanup();
        }
    }

}