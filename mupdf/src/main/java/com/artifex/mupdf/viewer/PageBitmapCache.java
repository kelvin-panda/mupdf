package com.artifex.mupdf.viewer;

import android.graphics.Bitmap;

import com.artifex.mupdf.util.Debugger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 已渲染页位图的 LRU 缓存（按"页号 + 渲染宽高"索引）。
 *
 * <p>解决的问题：手动点"上一页/下一页"会重建可视区的 {@link PageView}，没有这份缓存就只能对
 * 同一页反复 {@code loadPage + toDisplayList + 光栅化}，复杂矢量页要几百毫秒。有了它，
 * 已经渲染过的页可以直接贴图，跳过整个 MuPDF 渲染流程。</p>
 *
 * <p><b>所有权约定（关键，改动这里前务必遵守）：</b></p>
 * <ul>
 *   <li>缓存里的位图归缓存所有，{@link PageView} 只能显示、复用，<b>不得 recycle</b>；</li>
 *   <li>{@link #acquire} 命中后该位图被标记为 in-use，{@link PageView} 在换页/释放时必须调用
 *       {@link #release(Bitmap)} 归还（归还前先 {@code setImageBitmap(null)}）；</li>
 *   <li>in-use 的位图不会被淘汰也不会被再次发出，避免两个 View 同时写同一张位图；</li>
 *   <li>{@link #clear()} 对 in-use 的位图只做标记，等 {@code release} 到达时才真正 recycle。</li>
 * </ul>
 */
public final class PageBitmapCache {
    private static final String TAG = "PageBitmapCache";

    /**
     * 默认预算（下限）。
     *
     * <p>单页位图大小与屏幕/页面尺寸强相关（1080×1400 约 6MB，横屏适宽 1999×2830 约 21.6MB），
     * 固定值必然有一边不合适，所以默认是**自适应**：先给这个下限，等第一页渲染完知道单页字节数后，
     * 由 {@link #ensureBudgetForPage(long)} 抬到"覆盖可视 ±2 缓冲 + 2 页回看余量"。</p>
     *
     * <p>宿主只要显式设置 {@code MupdfConfig.Builder.pageBitmapCacheBytes(>0)}，就以宿主值为准，
     * 自适应不再生效。</p>
     */
    private static final long DEFAULT_MAX_BYTES = 24L * 1024L * 1024L;
    /** 自适应默认覆盖页数：可视 ±2（5 页）+ 2 页回看余量；宿主可用 {@link #setAutoCoverPages(int)} 覆盖 */
    private static final int AUTO_COVER_PAGES = 7;
    /** 自适应上限（普通设备） */
    private static final long AUTO_MAX_BYTES = 192L * 1024L * 1024L;
    /** 自适应上限（低内存设备，由宿主在打开时通过 {@link #setLowRamDevice} 告知） */
    private static final long AUTO_MAX_BYTES_LOW_RAM = 64L * 1024L * 1024L;
    /**
     * 显式指定页数时的硬上限（安全网）。
     *
     * <p>4K 平板适宽单页可达 ~79MB，若宿主配 14 页会算出 1.1GB —— 这里截断到 512MB，
     * 避免"配了页数就无限给内存"。达到上限时会在日志里标注"已按上限截断"。</p>
     */
    private static final long HARD_MAX_BYTES = 512L * 1024L * 1024L;

    /** 页数 × 单页字节，带上限；同时避免乘法溢出 */
    private static long pagesToBytes(int pages, long pageBytes, long cap) {
        if (pages <= 0 || pageBytes <= 0 || cap <= 0) return 0;
        if (pageBytes > cap / pages) return cap;
        return Math.min(cap, pages * pageBytes);
    }

    private volatile long maxBytes = DEFAULT_MAX_BYTES;
    /** true = 宿主未显式配置字节数，预算可随单页位图大小自适应；false = 以宿主值为准 */
    private boolean autoBudget = true;
    /** 自适应覆盖的页数（宿主可用 {@link #setAutoCoverPages(int)} 指定"缓存多少页"） */
    private int autoCoverPages = AUTO_COVER_PAGES;
    /** true = 页数是宿主显式指定的：按页数算预算，且不受自适应上限约束 */
    private boolean autoCoverPagesExplicit;
    /** 自适应上限（低内存设备会调低） */
    private long autoMaxBytes = AUTO_MAX_BYTES;

    private static final class Key {
        final int page;
        final int width;
        final int height;

        Key(int page, int width, int height) {
            this.page = page;
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key other = (Key) o;
            return page == other.page && width == other.width && height == other.height;
        }

        @Override
        public int hashCode() {
            int result = page;
            result = 31 * result + width;
            result = 31 * result + height;
            return result;
        }

        @Override
        public String toString() {
            return "page=" + page + "," + width + "x" + height;
        }
    }

    private static final class Entry {
        final Bitmap bitmap;
        /** 入缓存时记下字节数：Bitmap 被 recycle 后 getByteCount() 会返回 0，账目就对不上了 */
        final int bytes;
        boolean inUse;

        Entry(Bitmap bitmap, int bytes) {
            this.bitmap = bitmap;
            this.bytes = bytes;
        }
    }

    /** accessOrder=true：迭代顺序即 LRU 顺序（最近访问的在最后） */
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    /** 位图 → 键，便于 release 时反查 */
    private final Map<Bitmap, Key> keyByBitmap = new HashMap<>();
    /** clear() 时仍被 PageView 持有，等 release() 到达再 recycle，避免回收正在显示的位图 */
    private final Set<Bitmap> pendingRecycle = new LinkedHashSet<>();
    private long totalBytes;

    public PageBitmapCache() {
        // 自适应模式：先给下限，首帧渲染后按单页位图大小抬高
    }

    public PageBitmapCache(long maxBytes) {
        this.maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
        this.autoBudget = false;
    }

    /**
     * 显式设置预算（宿主通过 {@code MupdfConfig.pageBitmapCacheBytes} 配置）。
     *
     * <p>设置后**关闭自适应**；预算调到能覆盖整份文档时看过的页不再被淘汰，
     * 也就是"整份 PDF 页缓存"。调小会立即按 LRU 淘汰尚未显示的页。</p>
     */
    public synchronized void setMaxBytes(long maxBytes) {
        if (maxBytes <= 0) return;
        autoBudget = false;
        if (maxBytes == this.maxBytes) return;
        this.maxBytes = maxBytes;
        trim();
        Debugger.i(TAG, "setMaxBytes: " + maxBytes);
    }

    /** 低内存设备：自适应上限降到 64MB（只影响自适应，宿主显式配置不受影响） */
    public synchronized void setLowRamDevice(boolean lowRam) {
        autoMaxBytes = lowRam ? AUTO_MAX_BYTES_LOW_RAM : AUTO_MAX_BYTES;
    }

    /**
     * 显式指定"缓存多少页"（宿主通过 {@code MupdfConfig.pageBitmapCachePages} 配置）。
     *
     * <p>预算 = 页数 × 单页位图字节数，**不受自适应上限约束**（宿主既然指定了深度，就按它给）；
     * 显式配置过字节数（{@link #setMaxBytes}）时本设置不生效。</p>
     */
    public synchronized void setAutoCoverPages(int pages) {
        if (pages <= 0 || !autoBudget) return;
        autoCoverPages = pages;
        autoCoverPagesExplicit = true;
        Debugger.i(TAG, "setAutoCoverPages: " + pages);
    }

    /** 当前自适应覆盖页数（日志用） */
    public synchronized int getAutoCoverPages() {
        return autoCoverPages;
    }

    /** 是否仍处于自适应模式（宿主未显式配置预算） */
    public synchronized boolean isAutoBudget() {
        return autoBudget;
    }

    /**
     * 自适应模式：按单页位图字节数抬高预算，至少覆盖"可视 ±2 缓冲 + 2 页余量"。
     *
     * <p>固定预算小于在用缓冲时，缓存会被正在显示的页撑满、其余页一离开缓冲就被淘汰，
     * 等于白占内存还不起作用（实测默认 50MB 时 5 页缓冲已达 108MB）。
     * 宿主显式配置过预算时本方法不做任何事。</p>
     *
     * @param pageBytes 单页位图字节数（宽 × 高 × 4）
     */
    public synchronized void ensureBudgetForPage(long pageBytes) {
        if (!autoBudget || pageBytes <= 0) return;
        long cap = autoCoverPagesExplicit ? HARD_MAX_BYTES : autoMaxBytes;
        long target = pagesToBytes(autoCoverPages, pageBytes, cap);
        if (target <= maxBytes) return;
        long before = maxBytes;
        maxBytes = target;
        Debugger.i(TAG, "auto budget " + (before / 1024 / 1024) + "MB -> "
                + (maxBytes / 1024 / 1024) + "MB（单页 " + (pageBytes / 1024 / 1024)
                + "MB，覆盖 " + autoCoverPages + " 页"
                + (autoCoverPagesExplicit ? "，宿主指定" : "")
                + (target == cap ? "，已按上限 " + (cap / 1024 / 1024) + "MB 截断" : "") + "）");
        trim();
    }

    public synchronized long getMaxBytes() {
        return maxBytes;
    }

    /**
     * 取一页已渲染的位图。
     *
     * @return 命中返回位图（已被标记为 in-use，调用方负责 {@link #release}），未命中返回 null
     */
    public synchronized Bitmap acquire(int page, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        Key key = new Key(page, width, height);
        Entry entry = entries.get(key);
        if (entry == null) return null;
        Bitmap bitmap = entry.bitmap;
        if (bitmap == null || bitmap.isRecycled()) {
            removeEntry(key, entry);
            return null;
        }
        // 正被另一个 View 持有：宁可重渲染，也不要让两个 View 共享同一张可写位图
        if (entry.inUse) return null;
        entry.inUse = true;
        return bitmap;
    }

    /**
     * 渲染完成，把位图交给缓存（所有权移交）。成功后位图处于 in-use，直到 {@link #release}。
     *
     * @return true 表示缓存已接管该位图，调用方不得再 recycle 它
     */
    public synchronized boolean put(int page, int width, int height, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || width <= 0 || height <= 0) return false;
        Key key = new Key(page, width, height);
        Entry old = entries.get(key);
        if (old != null && old.bitmap == bitmap) {
            old.inUse = true;
            return true;
        }
        if (old != null) {
            removeEntry(key, old);
        }
        Entry entry = new Entry(bitmap, bitmap.getByteCount());
        entry.inUse = true;
        entries.put(key, entry);
        keyByBitmap.put(bitmap, key);
        totalBytes += entry.bytes;
        trim();
        return true;
    }

    /**
     * 归还位图的持有权。仅当该位图确实由缓存持有时才有意义。
     *
     * <p>调用方必须先把 {@code ImageView} 的图片引用清掉，这里可能立即 recycle（clear 之后的情况）。</p>
     */
    public synchronized void release(Bitmap bitmap) {
        if (bitmap == null) return;
        if (pendingRecycle.remove(bitmap)) {
            recycle(bitmap);
            return;
        }
        Key key = keyByBitmap.get(bitmap);
        if (key == null) return;
        Entry entry = entries.get(key);
        if (entry != null && entry.bitmap == bitmap) {
            entry.inUse = false;
        }
    }

    /** 丢弃全部缓存（页面内容变化/缩放变化/销毁时调用）。in-use 的位图等归还时再回收。 */
    public synchronized void clear() {
        int dropped = 0;
        int deferred = 0;
        Iterator<Map.Entry<Key, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            iterator.remove();
            totalBytes -= entry.bytes;
            keyByBitmap.remove(entry.bitmap);
            if (entry.inUse) {
                pendingRecycle.add(entry.bitmap);
                deferred++;
            } else {
                recycle(entry.bitmap);
                dropped++;
            }
        }
        if (dropped > 0 || deferred > 0) {
            Debugger.i(TAG, "clear: recycled=" + dropped + ",deferred=" + deferred);
        }
    }

    /** 当前占用的字节数（调试用） */
    public synchronized long bytes() {
        return totalBytes;
    }

    /** 当前缓存的页数（调试用） */
    public synchronized int size() {
        return entries.size();
    }

    private void removeEntry(Key key, Entry entry) {
        entries.remove(key);
        totalBytes -= entry.bytes;
        keyByBitmap.remove(entry.bitmap);
        if (entry.inUse) {
            // 仍被 View 显示，交给 release 回收
            pendingRecycle.add(entry.bitmap);
        } else {
            recycle(entry.bitmap);
        }
    }

    /** 超出上限时按 LRU 淘汰未被持有的页 */
    private void trim() {
        if (totalBytes <= maxBytes) return;
        Iterator<Map.Entry<Key, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && totalBytes > maxBytes) {
            Entry entry = iterator.next().getValue();
            if (entry.inUse) continue; // 正在显示的页不能淘汰
            iterator.remove();
            totalBytes -= entry.bytes;
            keyByBitmap.remove(entry.bitmap);
            recycle(entry.bitmap);
        }
        Debugger.d(TAG, "trim: bytes=" + totalBytes + ",pages=" + entries.size());
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
