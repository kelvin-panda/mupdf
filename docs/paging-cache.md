# 分页与页缓存（Paging & Page Cache）

本文记录 **7.0.12 起**的分页渲染与缓存结构（7.0.13 重构缓存/批注写路径、7.0.14 修打开与缩放渲染、7.0.15 低清首帧 + 渲染优先级调度 + 日志分级）。
改动这一块前请先读完"关键不变量"，其中任何一条被破坏都会表现为
**页面花屏 / 贴错页 / native 崩溃 / ANR**。

## 1. 三层缓存

| 层 | 位置 | 容量 | 失效时机 |
|---|---|---|---|
| `PageView` 窗口 | `ReaderView.mChildViews` | 可视页 ±2 页（`VISUAL_BUFFER_SCREENS`） | 窗口移动时 `recycleOutside()` 增量回收 |
| 页位图 LRU | `PageBitmapCache`（`MuPDFCore#getPageBitmapCache()`） | **默认自适应**：起步 24MB，首帧渲染后按单页位图抬到"覆盖可视 ±2 缓冲 + 2 页余量"（默认 7 页，普通设备上限 192MB、低内存设备 64MB）。宿主可配 `pageBitmapCachePages(int)`（回看深度，按页数算、不受自适应上限约束，但有 **512MB 硬上限**）或 `pageBitmapCacheBytes(long)`（按字节，**优先级最高**）；配到能覆盖整份文档即"整份页缓存" | `invalidatePageCache()`（批注/水印/签名表）、`PageAdapter#refresh()`（旋转/缩放重置）、`layout()`（reflowable 重排）、`onDestroy()` |
| 页显示列表 LRU | `MuPDFCore#displayListCache` | 32 页（`MAX_DISPLAY_LISTS`，native 内存无法度量，只能按页数兜底） | 同上；另加"渲染抛异常时淘汰该页" |

另外 `MuPDFCore#pageSizeCache`（页尺寸，PDF 点）是**无锁**的 `ConcurrentHashMap`，用于排版与图标计算，
命中时不 loadPage；`layout()` 时清空。

**为什么不做"整份位图缓存"**：1080×1400 一页 ARGB_8888 ≈ 6MB，横屏适宽 1999×2830 ≈ 21.6MB；
100 页就是 600MB~2GB。所以默认只保证"覆盖可视缓冲"（自适应，见上表），
是否缓存整份由宿主按设备内存显式配置。

**为什么默认不能是固定小值**：in-use 的页不可淘汰，固定预算小于在用缓冲时，缓存会被正在显示的页撑满，
其余页一离开缓冲立刻被淘汰 —— 白占内存还不起作用（实测默认 50MB 时 5 页缓冲已 108MB，
`trim: bytes=113143400 pages=5`）。所以默认按单页位图大小自适应。

## 2. 关键不变量

### 2.1 位图所有权（`PageBitmapCache`）

- 缓存里的位图**归缓存所有**，`PageView` 只能显示/复用，**不得 recycle**；
- `acquire()` 命中即标记 in-use；`PageView` 换页/释放时必须**先 `setImageBitmap(null)` 再 `release()`**；
- `clear()` 对 in-use 的位图只做标记（`pendingRecycle`），等 `release()` 到达才真正回收；
- in-use 的位图不会被淘汰、也不会被再次发出（避免两个 View 共享同一张可写位图）。

对应实现：`PageView#detachCachedBitmap()` / `ensureEntireBitmap()` 里的 `mEntireBmCached` 分支。
**新增"丢弃 PageView"的路径时，必须走 `onNotInUse → releaseResources → detachCachedBitmap`**，
否则 in-use 位图会滞留到下一次 `clear()`。

### 2.2 显示列表的线程归属

`DisplayList` 是 native 对象，**只允许创建它的线程使用**。缓存项记录了 owner 线程：

- 跨线程一律按未命中处理；
- 缩略图渲染传 `cacheDisplayList=false`（`ThumbnailAdapter`），既省不下时间也会占满缓存；
- 渲染任务走 `AsyncTask` 串行执行器，所有页渲染都在同一个线程上，因此 owner 恒为同一个线程。

### 2.3 `Rect.transform()` 会就地修改

`Rect#transform(Matrix)` **修改接收者**。缓存里的页面 `bounds` 必须用副本再 transform，
否则每次绘制都会把 bounds 再乘一次 `zoom`，页面越画越大（`MuPDFCore#drawPage` 里已有注释）。

### 2.4 渲染触发链路

```
PageAdapter#getView → PageView#setPage（只算尺寸，不渲染）
   → ReaderView#measureView → PageView#onMeasure(EXACTLY) → renderEntirePage()
   → (AsyncTask 串行) MuPDFCore#drawPage → 显示列表缓存命中/新建 → 光栅化
```

- `setPage` **不能**按 `mSize`（fit-page）直接渲染：连续拼页是"适宽"布局，两者不一致时每页会白渲染一整页；
- `onMeasure` 只在 `MeasureSpec.EXACTLY` 时触发渲染（第一次 UNSPECIFIED 测量拿的是 mSize）；
- 同页同尺寸的重复请求会被 `mInflightPage/mInflightWidth/mInflightHeight` 丢弃（`update` 请求不丢）。

### 2.5 跳页是增量的

`ReaderView#setDisplayedViewIndex` **不**再置 `mResetLayout`：只重锚 `mScrollY` 并清掉未结算的滚动量，
由 `recycleOutside` 增量回收/补齐。相邻页跳转时目标页的 View 和位图都还在，**一次渲染都不需要**。
缓冲页按"从焦点页向两侧扩散"的顺序加载（`buildLoadOrder`），因为 `AsyncTask` 串行，
入队顺序就是渲染顺序，目标页必须最先入队。

## 3. 单锁模型与 `postNative`

`MuPDFCore` 的所有 native 访问都在**同一把对象锁**里（方法签名 `synchronized`），而渲染一页可能要
数秒（115MB 文档实测 1.8~7.4s/页）。因此：

> **UI 线程绝对不要直接调用 `MuPDFCore` 的同步 native 方法。**

统一走 `MuPDFCore#postNative(Runnable)`（单线程、daemon、`onDestroy` 时 shutdown），结果用
`postToMain` 回到主线程。已改造的入口：

- 读大纲（`openOutlineAsync`）
- PDF 水印写入（设置弹窗 / 水印弹窗）
- 创建签名表、填写签名行（宿主调用与弹窗各一处）
- 签名提交（写入期间禁用提交/取消按钮，防重复与竞态）
- 自由文本批注
- reflowable 重排版（`relayoutDocument`）

**只读缓存、不加锁的方法**可以留在 UI 线程：`getPageSizeIfKnown()`、`isPageRenderBusy()`、
`pendingPageRenderCount()`、`countPages()`。

### 3.1 允许阻塞的两个"闸门"

- **进入批注**：`waitForRenderIdleThen()` —— 进入批注只读一次页尺寸（native），所以**只在本页尺寸
  未探明且本页正在渲染**时才在后台等它渲完（最多 8s，>600ms 才弹"正在渲染页面，请稍候…"，超时也进入）。
  不能按"整个渲染队列忙"判断：缩放后缓冲页还在渲、队列长时间为忙，会导致每次点批注都白等并弹提示
  （尺寸早已缓存，其实可以直接进）。
- **会话内笔画提交**：`commitStrokeSafely()` —— 会话中的补丁刷新渲染在跑时，笔画先进
  `pendingStrokeCommits` 队列，由后台线程等队列空闲后再在主线程逐笔提交（8s 超时也提交）。
  几何计算仍留在主线程（依赖 `ReaderView` 状态，不能跨线程）。
- **会话内文本标记**（高亮/下划线/删除线）：`runOnMainWhenRenderIdle()` —— 同样是"后台等空闲 →
  主线程执行"，但**超时也执行**（写操作不能丢），与"进入批注"那个可放弃的闸门区分开。
- **会话内擦除**：画板通过 `AnnotationArtBoard#setWriteScheduler(...)` 注入同一个调度器
  （`runOnMainWhenRenderIdle`），且一次手势内的擦除点**合并成一批**提交（一次 MOVE 一个点会排出
  几十次 native 写 + 几十次整页刷新；实测合并后 `[Write]` 从 8+ 次降到 1 次、`updatePage` 2 次）。
  "擦除完成"回调（`notifyEraseFinished`）会等这批写落地后再上报，避免对端漏删。
  调度器内部是**单线程**执行器，写顺序有保证、也不会堆线程。

> 计数口径：`pendingPageRenders` 同时覆盖**整页渲染**（`renderEntirePage`）与**补丁渲染**（`updatePatch`），
> 两者都在 `CancellableTaskDefinition#doCleanup` 里减数。缩略图让路和笔画排队都依赖这个计数，
> 新增渲染入口时必须成对调用 `beginPageRender()` / `endPageRender()`。

### 3.2 仍未迁移（需要返回值，或需宿主侧配合）

- 共享批注链路的宿主实现（`addSharedAnnotations`、`deleteAnnotationsByOperationIds`）：
  **库侧已双机验证通过**（2026-11 两台平板：B→A、A→B、擦除同步、强制/普通邀请、拒绝回传、
  渲染中收操作均正常，见 5.6/6 的实测数据）；宿主侧那几处仍未改成"后台收集结果 → 主线程刷新"，
  属于宿主 App 的改造范围，不是库的问题。
- 批注会话内的 `addTextMarkupAnnotation`、`deleteLastAnnotation`、`markLocallyErasable`：
  文本标记与擦除已覆盖（3.1）；`deleteLastAnnotation` / `markLocallyErasable` 仍主线程直调。

## 4. 日志与排障

统一 tag 为 `mupdf`，前缀可 grep：

| 前缀 | 级别 | 含义 |
|---|---|---|
| `[Open]` | i | 页数 + 位图缓存预算 |
| `[Nav]` | i | 跳页目标/来源/scrollY |
| `[Layout]` | i | 可见范围/缓冲窗口/焦点页/新建/回收（仅在子 View 变化时打印） |
| `[Layout] getPageSizeSync ... 阻塞` | i | 主线程等 native 锁 >50ms（**出现即说明有新的阻塞路径**） |
| `[PageLoad] page=x/y visible` | i | **页码加载完成**（页码标签已更新） |
| `[PageLoad] page=x/y DONE cost=...` | i | 整页渲染完成（含耗时与位图尺寸） |
| `[Render] page=x/y dl=hit/build load= build= run= total= draw#n` | i | 一次 native 绘制：载页/建显示列表/光栅化三段耗时 + 该页第几次绘制 |
| `[Thumb] 页面渲染排队中… 让路` | i | 缩略图预取为页渲染让路 |
| `[PageLoad] setPage/render submit/deferred/discarded` | d | 每页记账日志（默认 d，避免发布版噪音） |

典型现象对照：

- 同一页出现 `draw#2` 且没有缩放/批注 → 重复渲染，检查 `setPage` 是否又在渲染；
- `[Layout] ... 阻塞` 反复出现 → 有新的 UI 线程 native 调用；
- 回看命中不了 `bitmapCache=HIT` → 预算小于单页位图（看 `[Open] bitmapCacheBudget`）。

## 5. 已知坑 / 后续工作

1. **预渲染不可行**：`MuPDFCancellableTaskDefinition#doCancel()` 故意不 `cookie.abort()`
   （abort 会让 native device 栈残留导致 SIGSEGV），渲染一旦开始不可抢占，
   后台预渲染只会拖慢用户当前页。要"点哪都秒开"必须先做渲染优先级队列。
2. **共享批注链路的宿主改造**（`addSharedAnnotations` 等）仍未做，属宿主 App 范围；
   库侧链路已于 2026-11 双机实测通过（见 3.2）。
3. **大文档首次布局**会对全部页做一次 `loadPage` 取尺寸（约 1~3ms/页）。
   实测发生在任何渲染开始之前，不会等锁；若要彻底去掉，需要"估算定位 + 拿到真实尺寸后自校正"的改造。
4. 页位图预算默认自适应（`24MB → 页数 × 单页位图`，默认 7 页、上限 192MB / 低内存 64MB）。
   实测横屏适宽单页 21.6MB 时默认预算抬到 151MB，回看 2~3 页可命中；再远仍会重渲染。
   想加深回看用 `MupdfConfig.Builder.pageBitmapCachePages(int)`（例如 14 页 → 302MB），
   或直接给 `pageBitmapCacheBytes`（优先级最高）。显式页数模式另有 **512MB 硬上限**
   （4K 平板单页可达 ~79MB，14 页就是 1.1GB，超限会在日志里标注"已按上限 512MB 截断"）。
5. **撤销/取消链路的写**：`deleteLastAnnotation`、`markLocallyErasable`（取消批注、远端撤销）
   仍在主线程直接调 `MuPDFCore`，未走调度器。擦除（画板内 `deleteAnnotationWithOperationIds`）
   已改为批量 + 调度，见 3.1。
6. **双指缩放不能每帧重渲染**。`onScale` 每回调一次就 `requestLayout`，若照常按新尺寸渲染，
   一次捏合会产生十几次整页渲染（重页 ~200-800ms/次），手感"一跳一跳"。
   现在 `onScaleBegin` 打开 `PageView#setScalePreview(true)`：手势期间 `onMeasure`/`onLayout`
   只把已有位图按新尺寸拉伸（`mEntireMat`），`onScaleEnd`/`ACTION_CANCEL` 再调
   `renderIfSizeChanged()` 按最终尺寸补一次（先补可见页）。设置页的缩放滑块同样处理。
   实测：连续改 49 次缩放 → 拖动期 0 次渲染，松手后每页 1 次。
   **松手补渲染必须走"静默更新"（`update=true`）**：普通 `draw` 路径在尺寸变化时会
   `clearEntireBitmap` 掉旧位图 → ImageView 变空、出现白屏 + 转圈（用户可见的"抬手后白屏再加载"）；
   `update=true` 时 `quietUpdate` 分支渲染到临时位图、完成后原子替换，旧图全程可见。
7. **初始缩放必须在首次布局前生效**。`applyZoomPercent` 如果放在 `postToMainDelayed` 里，
   首次布局会先按 `mScale = 1.0`（适宽 100%）渲一遍，post 生效后再按目标尺寸重渲一遍 ——
   打开耗时接近翻倍。实测（3 页、页 1）：42% 时 `2000x2833 847ms + 840x1190 210ms`，
   `DONE cost=1808ms`；100% 时只有 `2000x2833`，`DONE cost=886ms`（第二遍被"同页同尺寸"去重）。
   修法：`createUI` 里在首次布局前就 `applyZoomPercent`，并且 `ReaderView.defaultScale` 要同步更新
   `mDefaultScale`（否则 `refresh()`（首次 `onSizeChanged`/旋转）会把 `mScale` 冲回 1.0）。
   修复后 42% 只渲一遍 `840x1190`，`DONE cost=336ms`。

8. **低清首帧 + 空闲补高清（7.0.15）**：首帧像素 ≥ 预算 × 3 时，先按 `LOW_RES_PIXEL_BUDGET`（0.6MP）
   渲一张小图立即上屏，随后在本页仍可见、且用户没在拖动/缩放时调 `refineIfLowRes()` 补高清
   （`update=true` 静默替换）。**低清图不能入页位图缓存**（缓存 key 是逻辑尺寸，会把后续高清请求毒化成糊图）。
   滚动/惯性期间用更小预算（`SCROLL_LOW_RES_BUDGET = 0.25MP`）出预览，settle 后统一补高清。
9. **渲染必须走 `RenderScheduler`（单线程 + 优先级，7.0.15）**：显示列表缓存按线程归属，
   渲染换线程就会 `dl` 全部 miss（每页多 40~140ms）；两级队列保证可见页/补高清插队、缓冲页与缩略图让路。
   **取消只对排队中的任务有效**：渲染一旦开始不可中断（不 abort），因此 `PageView.reinit()` 必须在
   `cancel()` 前把正在写的位图从 View 上摘下来（`abandonEntireBitmapForCancelledRender()`），
   由任务的 `doCleanup()` 结束后回收；否则下一次 `clearEntireBitmap()` 会 recycle 掉正在被 native 写的位图。
   另：`cancel()` **不能**在主线程等渲染结束（重页 4~7s → 看门狗/ANR）。
10. **日志级别（7.0.15）**：`MupdfConfig.Builder.verboseLogEnabled(false)` 默认只输出异常与关键信息；
   `d` 级日志（`[Render]` 三段耗时、`gotoPage`、`defaultScale`、`onLayout2`、装页等）需要显式开启，
   热路径日志同时用 `Debugger.isDebug()` 守卫避免拼字符串。`bundle config`（开档全量配置）保留在 INFO。
