# EventBus → Port/Session 迁移设计稿

> **状态**：**已落地（P1~P3 完成，P2c 待做）** —— 见下方「实施状态」
> **目标版本**：7.0.0（major，允许破坏性改动）
> **前置决策**：① 允许破坏性改动；② 按"进程内可能并存多个会话"设计；③ 入站调用方：设计阶段按"页面级"评估，实测为 **Service 持有的 app 级单例**（见实施状态 §D2）
> **本文档的用途**：作为 P1~P4 的施工图。第 3 章是契约定义，第 4/5/6 章是逐点对照清单，第 7 章交给依赖端（宿主 App）团队。

---

## 实施状态

### 已完成

| 阶段 | 状态 | 产物 |
|---|---|---|
| P1 新增 `api` 包 | ✅ | `MupdfHost` / `MupdfSession` / `MupdfSessions` / `MupdfExitReason` / `MupdfResult`；`MupdfConfig` 新增 `sessionId` / `host` / `shareAnnotationEnabled` / `hengXunStyle` |
| P2 Activity 接线 | ✅ | 13 个出站点改 `dispatchHost(...)`；`@Subscribe` → `SessionImpl`；`startSession()` / `sessionEnded(reason)`；`jump()` fail-fast |
| P3 破坏性收尾 | ✅ | 删除 `bus/` 三个类与 `eventbus` 依赖；`MupdfInkBean`/`MupdfOperationId` 移入 `api`；移除 7 个会话级静态字段与 `reset()`；版本 → 7.0.0 |
| P4 文档与示例 | ✅ | `example` 改为 `MupdfHost` 示范；`README` / `annotation-flow.md` / `AGENTS.md` / `CLAUDE.md` 更新 |

### 未完成 / 已知遗留

| 项 | 说明 |
|---|---|
| **P2c：显示类静态字段** | `MupdfMacro.clarityLimitMode` / `backgroundColor` / `brightness` 仍为 `public static`；`PageView` 有 8 个读取点（`setBackgroundColor` 4 处、`buildColorFilter()` 1 处、`clarityLimitMode` 3 处）。**实际影响**：进程内并存两个阅读器时纸张色/亮度/清晰度互相干扰。单会话场景无影响，因此未纳入 7.0.0 |
| **`MupdfLauncher`** | 设计中的 `ActivityResultContract` 便利路径未实现；宿主统一走 `MupdfHost` 即可，`MupdfResult` 已预留 |
| **`pdfArtBoardPicture` 等宿主侧协议** | 属于宿主 App 的协议层，不在本库范围（`hengxun` 已在自身 `JniHelper` 中补齐） |

### 与设计稿的偏差（按实际实现修正）

**D1. `MupdfSession` 比设计稿多了两个只读能力**

实际实现增加了 `mediaId()` 与 `hasDocument(int)` —— 因为宿主原本靠 `MupdfMacro.currentMediaId` 判断"当前打开的是哪个文件"，删掉静态字段后必须有等价能力。

**D2. 入站调用方不是"页面级"**

原设计假设由宿主页面持有 `MupdfSession`。实测（`Paperless` 的 `PDFAnnotationEvent`）由 `FrontDeskService` 持有，是 **Service 级 app 单例**。
这反而让设计更简单：**打开方 = 接收方**，`onSessionReady` 拿到的句柄可以一直持有到 `onSessionClosed`，`MupdfSessions.find()` 退化为"页面重建后重连"的可选兜底。

**D3. `MupdfHost` 是接口、不能进 Bundle**

`MupdfConfig.host(MupdfHost)` 无法经 Intent 传递，实际用 `PENDING_HOSTS`（`sessionId → host` 静态表）在 `jump()` 与 Activity 之间交接，Activity 重建后仍能取回同一个 host。
一旦「宿主忘了调 `host(...)`」就变成运行时静默丢事件，因此在 `jump()` 里做了 fail-fast：`host == null` 直接抛 `IllegalStateException`。

**D4. `onAnnotationSessionExited` 改为无条件回调**

设计稿里它受 `MupdfMacro.isSharing` 保护；7.0.0 删掉该字段后改为**总是回调**（`sessionEnded()` 内部），由宿主判断是否发协议。
⚠️ 副作用：Activity 重建（旋转 / `onNewIntent` → `recreate()`）也会走一次会话结束，宿主若直接发「退出共享」会误断。宿主侧需做「重建宽限」判断（`hengxun` 用 400ms 延迟 + 复查 `isActive()`）。

**D5. 触摸事件不做节流**

设计稿 O1 建议 1s 节流；实际按「库只做中性回调、节流交给宿主」实现，因为库无法判断宿主的用途（例如"悬浮控件隐藏时才需要唤醒"的边沿触发）。

---

## 0. 章节与阶段对应

| 阶段 | 内容 | 对应章节 |
|---|---|---|
| P1 | 新增 `com.xlk.mupdf.library.api` 包，纯新增，不改旧代码 | 第 3 章 |
| P2 | Activity 内部改造 + `MupdfMacro` 收编 | 第 4、5、6 章 |
| P3 | 删除 `bus/` 包与 EventBus 依赖，版本 → 7.0.0 | 第 8 章 |
| P4 | example 重写、文档与 README 更新 | 第 7、9 章 |

---

## 1. 现状快照

### 1.1 现在的"沟通"其实有四条通道

```
依赖端 App ──① 配置──> 库      MupdfConfig.Builder（设计正确，但没覆盖全部开关）
依赖端 App ──② 指令──> 库      EventBus.post(MupdfEventMessage) → Activity.@Subscribe   ← 入站
库         ──③ 通知──> 依赖端  MupdfBus.post(type, Object...) → 宿主 @Subscribe          ← 出站
依赖端 App ──④ 写静态字段─> 库  MupdfMacro.public static（共享开关、纸张色、亮度…）
```

通道 ④ 经常被忽略，但它是实际存在、且比 ③ 更危险的接口：`MupdfMacro.isSharing` 在库内**从未被置为 true**，只有 `reset()` 里置 false —— 共享批注的开关就是宿主持有 `MupdfMacro` 引用直接写静态布尔值。`MupdfConfig` 里已有 `clarityLimitMode` 却同时存在 `MupdfMacro.clarityLimitMode`，双源。

### 1.2 17 条消息全表

出站 9 条（库 → 宿主）：

| # | type | post 点 | 实际载荷 | 线程 | 备注 |
|---|---|---|---|---|---|
| 1 | `inform_screenshot` | `MuPdfDocumentActivity:1432` | `(String mDocTitle, int 0)` | main（延迟 250ms） | 第二参数恒为 `0`，无意义 |
| 2 | `inform_upload` | `:2654` | `(String savePath, int uploadDirId)` | **后台线程**，靠 `ThreadMode.MAIN` 兜底 | 之后延迟 2000ms `finish()` |
| 3 | `out_open_inform` | `:1421`、`:2079` | `(String srcFilePath, String srcUri)` | main | **在 `finish()` 之后** post |
| 4 | `inform_invite_annotation` | `:1711` | `(String mDocTitle, int mediaId, int page)` | main | |
| 5 | `inform_share_annotation` | `:1389`、`:1949`、`:2414` | `(List<MupdfInkBean>)` | main | 3 处调用点，载荷语义已从"只有 ADD"扩到 ADD/ERASE/UNDO |
| 6 | `inform_exit_annotation` | `:3547`（`onDestroy`） | `(int mediaId)` | main | |
| 7 | `inform_touch_device` | `:274`（`dispatchTouchEvent`） | 无 | main | 每次 `ACTION_DOWN` 都发，无节流 |
| 8 | `inform_inform_signature` | `:1311` | `(int mediaId)` | main | |
| 9 | `result_signature` | `:553`、`:1082` | `(byte[] bmpBytes)` | main | |

入站 8 条（宿主 → 库），全部在 `MuPdfDocumentActivity:482` 的 `switch` 里：

| # | type | 分支 | 现在做什么 |
|---|---|---|---|
| 10 | `receive_annotation_info` | `:501` | `enqueueSharedOperations(objects[0])` —— 核心链路 |
| 11 | `receive_invite_annotation` | `:486` | **仅 `Debugger.e()`** |
| 12 | `receive_reject_annotation` | `:491` | **仅 `Debugger.e()`** |
| 13 | `receive_exit_annotation` | `:496` | **仅 `Debugger.e()`** |
| 14 | `close_mupdf` | `:509` | `exit()` |
| 15 | `mupdf_create_signature_row` | `:514` | 建签名表 |
| 16 | `receive_inform_signature` | `:541` | 弹签名画板 |
| 17 | `submit_member_signature` | `:561` | 写入签名表 |

**17 条里有 3 条是空实现（18%）**，这本身就是契约腐化的直接证据。

### 1.3 缺陷登记（带位置，供 P2 逐条勾销）

| ID | 缺陷 | 位置 | 严重度 |
|---|---|---|---|
| D1 | **广播语义，无会话标识**。`close_mupdf`/`receive_annotation_info`/`submit_member_signature` 会打到所有存活的 Activity 实例 | `:482` | 高（正确性） |
| D2 | **双重静态源**。`MupdfMacro.clarityLimitMode/backgroundColor/brightness` 与 `MupdfConfig` 各存一份，`applyDocumentState()` 每次覆盖 | `:897-899` | 高（并存下串台） |
| D3 | **运行时改全局纸张色**。设置对话框直接写 `MupdfMacro.backgroundColor`，`PageView` 经 `buildColorFilter()` 读同一字段 | `:3140/3173/3192/3339`、`MupdfMacro:113-139` | 高（并存下 A 的会话改掉 B 的显示） |
| D4 | **无类型契约**。`(int) msg.getObjects()[0]`、`(String) objects[0]` 全靠 `MupdfBusType` 注释 | `:562`、example `:105` | 高（演进时静默崩） |
| D5 | **非 sticky，晚注册丢事件**；`onDestroy` 后 `MupdfMacro.reset()`，事件静默丢弃 | `:482`、`:3556` | 中 |
| D6 | **无幂等**。`receive_inform_signature` 收到就弹画板，网络重发即弹两次 | `:544` | 中 |
| D7 | **`finish()` 之后再 post**，依赖 EventBus 同步分发才没丢 | `:1421`、`:2079` | 中 |
| D8 | **`api` 泄漏依赖**，宿主被迫感知 EventBus 版本 | `mupdf/build.gradle:59` | 中 |
| D9 | **R8 脆弱**。AAR 分发 + `@Subscribe` 反射，宿主裁掉回调时编译期零提示 | `:482` | 中 |
| D10 | **无鉴权公共入口**。`@Subscribe public` 方法，进程内任意代码可调 | `:483` | 低 |
| D11 | 死字段：`MupdfEventMessage.method` 从未被赋值；`MupdfMacro.sharingIds/launchSrcwbid/launchSrcmemid/currentMediaId/currentFilePath/currentUri` 库内**零读取** | 见第 5 章 | 低 |
| D12 | example 注册后从不注销 | `example/MainActivity.java:50` | 低（错误示范） |

---

## 2. 目标架构

### 2.1 结构

```
依赖端 App                         库
┌──────────────────────┐    ┌───────────────────────────────┐
│ MupdfConfig.Builder   │───>│ MuPdfDocumentActivity         │
│   .sessionId(...)     │    │   └─ MupdfSessionImpl         │
│   .host(MupdfHost) ───┼───>│        (定向、可并存)          │
│                       │    │                               │
│ MupdfHost 实现   <────┼────│  出站：直接调 host.xxx()       │
│   onSessionReady(◄────┼────│  onSessionReady(session)      │
│   onLocalOperations…  │    │                               │
└──────────────────────┘    └───────────────────────────────┘
        ▲                                   │
        │  MupdfSessions.find(sessionId)    │ 唯一静态入口
        └───────────────────────────────────┘  (仅路由，非广播)
```

### 2.2 四条设计原则

1. **依赖倒置**：库不"知道"宿主有网络层，只声明自己需要什么能力（`MupdfHost` 端口）。宿主不"猜"库会发什么，只实现接口。
2. **定向优于广播**：一次打开 = 一个 `MupdfSession`，入站必须走句柄。并存是默认假设，不是特例。
3. **线程与生命周期显式化**：EventBus 免费赠送的 `ThreadMode.MAIN` 和注册/注销时机，必须在接口注释里写死，并由库实现保证。
4. **配置只有一个来源**：能进 `MupdfConfig` 的一律进 config；只有真正的会话内运行时状态才留在库里，且不对外暴露静态字段。

### 2.3 为什么不是其它方案

| 方案 | 结论 |
|---|---|
| 保留 EventBus，只把 String 换成事件类 | 解决 D4，不解决 D1/D2/D5/D8。作为过渡可以，但不是终点 |
| LiveData / Flow / RxJava | 可做在 `MupdfHost` 之上的可选薄适配，不做主结构（库是 Java + minSdk 21，不引入 Kotlin/coroutines 依赖） |
| ActivityResultContract | 装不下实时共享批注（双向流），只用于**终态结果**，作为 `MupdfHost` 的补充而非替代 |
| AIDL / Messenger / LocalBroadcastManager | 单进程库，过重；LBM 已 deprecated。排除 |
| 保留 `EventBus.getDefault()` 但内部转发 | 见 P3 目标：依赖彻底移除，不做双层 |

---

## 3. 契约定义（P1 产出）

> 以下为该阶段要新增的源码骨架。签名按此实现，Javadoc 中的括号是**必须兑现的契约**，不是描述性说明。

### 3.1 `api/MupdfHost.java`

```java
package com.xlk.mupdf.library.api;

import java.util.List;

/**
 * 库向宿主提供的能力端口（库 → 宿主）。
 *
 * <p><b>线程契约</b>：本接口所有方法都在主线程回调。</p>
 * <p><b>生命周期契约</b>：在 {@link #onSessionReady} 与 {@link #onSessionClosed} 之间有效；
 * {@code onSessionClosed} 之后库不再持有本对象引用。</p>
 * <p><b>迁移契约</b>：所有方法均为 default 空实现，升级库新增回调不会导致宿主编译失败。</p>
 */
public interface MupdfHost {

    /**
     * 会话就绪。此时 core 已可用、文档已解析完成、可以接受入站指令。
     *
     * <p><b>可能被多次回调</b>：宿主页面重建（旋转、进程内 Activity 重建）后，
     * 库会以<b>相同 sessionId</b>再次回调本方法并给出新的 {@link MupdfSession} 句柄。
     * 宿主应按 sessionId 去重，不要假定只回调一次。</p>
     *
     * <p>若宿主未持有句柄（例如页面被销毁后重建），可用
     * {@link MupdfSessions#find(String)} 按 sessionId 重新获取。</p>
     */
    default void onSessionReady(MupdfSession session) {}

    /**
     * 会话结束。保证在 Activity 销毁前回调，且<b>只回调一次</b>。
     * 宿主应在此释放 session 引用、停止向该会话投递事件。
     *
     * @param reason 结束原因，见 {@link MupdfExitReason}
     */
    default void onSessionClosed(String sessionId, @MupdfExitReason int reason) {}

    // ---- 原出站 9 条 ----

    /**
     * 本地产生了批注操作（原 {@code inform_share_annotation}）。
     * 载荷可能含 OP_ADD / OP_ERASE / OP_UNDO，宿主应原样转发给其它端。
     */
    default void onLocalOperations(List<MupdfInkBean> operations) {}

    /** 已保存成功，请宿主上传（原 {@code inform_upload}）。 */
    default void onUploadRequested(String savedPath, int uploadDirId) {}

    /** 用户点了截图（原 {@code inform_screenshot}）。原第二参数恒为 0，已移除。 */
    default void onScreenshotRequested(String docTitle) {}

    /** 需要宿主用外部应用打开该文件（原 {@code out_open_inform}）。 */
    default void onOpenExternally(String filePath, String uri) {}

    /** 邀请他人参与批注（原 {@code inform_invite_annotation}）。 */
    default void onAnnotationInviteRequested(String docTitle, int mediaId, int pageNumber) {}

    /** 批注会话退出（原 {@code inform_exit_annotation}）。 */
    default void onAnnotationSessionExited(int mediaId) {}

    /** 秘书端请求他人签名（原 {@code inform_inform_signature}）。 */
    default void onSignatureInviteRequested(int mediaId) {}

    /** 签名已采集，交宿主回传（原 {@code result_signature}）。 */
    default void onSignatureCaptured(byte[] bmpBytes) {}

    /**
     * 用户在阅读器上有触摸（原 {@code inform_touch_device}）。
     * 库已做节流（默认 1s 内最多一次），宿主无需再自行去重。
     */
    default void onUserTouched() {}
}
```

### 3.2 `api/MupdfSession.java`

```java
package com.xlk.mupdf.library.api;

import java.util.List;

/**
 * 一次文档阅读会话的句柄（宿主 → 库）。
 *
 * <p><b>线程契约</b>：所有方法可从任意线程调用，库内部切到主线程处理。</p>
 * <p><b>生命周期契约</b>：仅在 {@link MupdfHost#onSessionReady} 之后、
 * {@link MupdfHost#onSessionClosed} 之前有效；之后调用为安全空操作（不抛异常）。</p>
 * <p>{@link #isActive()} 可从任意线程读取。</p>
 */
public interface MupdfSession {

    /** 会话标识，来自 {@code MupdfConfig.Builder.sessionId(String)}，配置变更前后不变。 */
    String id();

    /** 是否仍可投递指令。宿主网络层在投递前应先检查，避免静默丢弃。 */
    boolean isActive();

    /**
     * 投递远端批注操作（原 {@code receive_annotation_info}）。
     *
     * <p>{@code operationId} 相同的操作只会生效一次；{@code operationId <= 0} 的操作
     * 由库内部补号（沿用 {@code normalizeSharedOperation} 的现有逻辑）。
     * 这里接受单条、列表或二者混合，无需宿主自行判断类型。</p>
     */
    void receiveRemoteOperations(List<MupdfInkBean> operations);

    /** 创建签名表（原 {@code mupdf_create_signature_row}）。重复调用以首次为准。 */
    void createSignatureTable(List<String> memberNames, List<Integer> memberIds);

    /** 提交某成员的签名位图（原 {@code submit_member_signature}）。 */
    void submitMemberSignature(int memberId, byte[] bmpBytes);

    /** 请求本机弹签名画板（原 {@code receive_inform_signature}）。库内已保证同一时刻只弹一个。 */
    void requestHostSignature();

    /**
     * 请求关闭阅读器（原 {@code close_mupdf}）。
     * 语义与用户按返回键一致：有未保存批注时仍会弹保存提示。
     * 若宿主需要"强制关闭不提示"，使用 {@link MupdfSessions#forceClose(String)}。
     */
    void close();
}
```

### 3.3 终态结果与 Launcher

```java
package com.xlk.mupdf.library.api;

public interface MupdfExitReason {
    int UNKNOWN = 0;
    /** 用户退出，未保存（含"不保存"选择）。 */
    int EXITED_UNSAVED = 1;
    /** 已保存并已回调 onUploadRequested。 */
    int SAVED = 2;
    /** 保存失败。 */
    int SAVE_FAILED = 3;
    /** 文件无法打开，用户选择"确定"（随后会回调 onOpenExternally）。 */
    int OPEN_FAILED = 4;
    /** 用户选择用外部应用打开（随后会回调 onOpenExternally）。 */
    int OPEN_EXTERNAL = 5;
    /** 宿主调用 session.close()。 */
    int CLOSED_BY_HOST = 6;
    /** 密码输入取消。 */
    int PASSWORD_CANCELLED = 7;
    /** 同一 Activity 被新的打开请求替换（onNewIntent）。 */
    int REPLACED = 8;
}

/** 终态快照，仅用于 ActivityResultContract 路径。 */
public final class MupdfResult {
    public final String sessionId;
    public final @MupdfExitReason int reason;
    public final String savedPath;   // 仅 SAVED 时非空
    public final boolean documentModified;
    // 构造与 getter 略
}

/**
 * 可选路径：宿主用 registerForActivityResult 拿终态，无需实现 MupdfHost。
 * 适合"打开 → 保存 → 关闭"的简单页面。与 MupdfHost 可同时使用（result 在 finish 时产出）。
 */
public final class MupdfLauncher
        extends androidx.activity.result.contract.ActivityResultContract<MupdfConfig, MupdfResult> {
    // createIntent：内部等价于 MuPdfDocumentActivity.jump(context, config)
    // parseResult：从 Intent extras 读 MupdfResult
}
```

### 3.4 `api/MupdfSessions.java`（唯一静态入口）

```java
package com.xlk.mupdf.library.api;

/**
 * 会话路由表。<b>不是通信通道</b>：没有广播、没有按类型分发，
 * 只能按显式 sessionId 取到唯一句柄，且返回对象自带 isActive()。
 */
public final class MupdfSessions {
    private MupdfSessions() {}

    /** 按 id 查会话；不存在或已结束返回 null。 */
    public static MupdfSession find(String sessionId) { ... }

    /** 当前进程内所有存活会话 id，用于调试与泄漏排查。 */
    public static java.util.List<String> activeSessionIds() { ... }
}
```

实现要点：
- 内部 `ConcurrentHashMap<String, MupdfSessionImpl>`，**仅**供 `find` 使用；
- 会话就绪时注册、`onSessionClosed` 时移除；`Activity.onDestroy` 必须兜底移除（防孤儿项）；
- 库**不用**弱引用（会因 GC 提前失效导致"莫名丢事件"），改用"生命周期内强引用 + 结束时置空"。

### 3.5 `MupdfConfig` 新增字段

| Builder 方法 | 必填 | 说明 |
|---|---|---|
| `sessionId(String)` | **是** | 并存路由键。库内为空时自动生成并打 warn（兼容旧调用） |
| `host(MupdfHost)` | 建议 | 为 null 时打 warn；若同时未用 `MupdfLauncher`，`jump()` 抛 `IllegalStateException`（fail-fast，见第 8 章） |
| `shareAnnotationEnabled(boolean)` | 否 | 原 `MupdfMacro.shareAnnotationEnable` |
| `hengXunStyle(boolean)` | 否 | 原 `MupdfMacro.isHengXunVersion` |
| `allowDeleteHistoryAnnotation(boolean)` | 否 | 原 `MupdfMacro.delete_history_annotation` |

### 3.6 契约汇总表

| 项 | 约定 |
|---|---|
| `MupdfHost` 回调线程 | 主线程（库内 `Handler` 保证） |
| `MupdfSession` 调用线程 | 任意线程 |
| `isActive()` | 任意线程，`volatile` 读 |
| `onSessionReady` 次数 | ≥1，每次 Activity 实例一次；同 sessionId 去重 |
| `onSessionClosed` 次数 | 恰好 1 次，且在 Activity 销毁前 |
| `onSessionClosed` 之后调 session | 安全空操作 |
| 库持有 host 引用 | 仅会话期间；结束时置 null |

---

## 4. 逐点改造对照（P2 施工清单）

### 4.1 出站 13 个 post 点

| 编号 | 位置 | 现在 | 改为 |
|---|---|---|---|
| O1 | `:274` `dispatchTouchEvent` | `MupdfBus.post(inform_touch_device)` | `dispatchHost(h -> h.onUserTouched())`，**并加节流**：`if (now - lastTouchNotify < 1000) return;` |
| O2 | `:553` 签名画板回调 | `MupdfBus.post(result_signature, bytes)` | `dispatchHost(h -> h.onSignatureCaptured(bytes))` |
| O3 | `:1082` `shouldSignature` 分支 | 同上 | 同上（与 O2 抽同一个私有方法 `notifySignatureCaptured(byte[])`） |
| O4 | `:1311` 通知签名按钮 | `MupdfBus.post(inform_inform_signature, mediaId)` | `dispatchHost(h -> h.onSignatureInviteRequested(mediaId))` |
| O5 | `:1389` 提交签名后共享 | `MupdfBus.post(inform_share_annotation, annotationBeans)` | `dispatchHost(h -> h.onLocalOperations(annotationBeans))` |
| O6 | `:1421` 外部打开 | `finish(); MupdfBus.post(out_open_inform, srcFilePath, srcUri);` | **顺序反转**：先 `notifyOpenExternally()`，再 `sessionEnded(OPEN_EXTERNAL)`，最后 `finish()` |
| O7 | `:1432` 截图 | `MupdfBus.post(inform_screenshot, mDocTitle, 0)` | `dispatchHost(h -> h.onScreenshotRequested(mDocTitle))` |
| O8 | `:1711` 邀请批注 | `MupdfBus.post(inform_invite_annotation, mDocTitle, mediaId, page)` | `dispatchHost(h -> h.onAnnotationInviteRequested(mDocTitle, mediaId, page))` |
| O9 | `:1949` `rememberLocalOperation` | `MupdfBus.post(inform_share_annotation, singletonList(op))` | `dispatchHost(h -> h.onLocalOperations(Collections.singletonList(op)))` |
| O10 | `:2079` 打开失败→外部打开 | 同 O6 的写法 | 同 O6 |
| O11 | `:2414` 取消批注的撤销共享 | `MupdfBus.post(inform_share_annotation, undoOperations)` | `dispatchHost(h -> h.onLocalOperations(undoOperations))` |
| O12 | `:2654` 保存成功 | 后台线程 `MupdfBus.post(inform_upload, savePath, uploadDirId)` | `postToMain(() -> dispatchHost(h -> h.onUploadRequested(savePath, uploadDirId)))`，然后 `sessionEnded(SAVED)` |
| O13 | `:3547` `onDestroy` | `MupdfBus.post(inform_exit_annotation, mediaId)` | `dispatchHost(h -> h.onAnnotationSessionExited(mediaId))`，见第 6 章时序 |

需要新增的两个私有工具方法（都在 `MuPdfDocumentActivity` 内）：

```java
/** 出站总闸：宿主未注入时静默跳过，避免 NPE；会话结束后不再回调。 */
private void dispatchHost(java.util.function.Consumer<MupdfHost> action) {
    if (!sessionActive || host == null) return;
    action.accept(host);
}

/** 会话结束（幂等），见第 6 章。 */
private void sessionEnded(@MupdfExitReason int reason) { ... }
```

> 用 `Consumer<MupdfHost>` 的好处：13 个改造点都是机械替换，不引入新抽象；且天然把"会话未就绪"的判断收在一处。

### 4.2 入站 8 个分支

`@Subscribe public void eventBus(MupdfEventMessage)`（`:482-595`）整体删除，由 `MupdfSessionImpl` 承载。映射：

| 原分支 | 新实现位置 | 说明 |
|---|---|---|
| `receive_annotation_info` | `session.receiveRemoteOperations(List)` | 内部仍调 `normalizeSharedOperation` → `pendingSharedOperations` → `scheduleSharedOperationDrain`，**逻辑不动**。区别是入参已是 `List<MupdfInkBean>`，`enqueueSharedOperations(Object payload)` 的 `instanceof` 分派（`:1749-1758`）可删 |
| `receive_invite_annotation` | **删除** | 现在只有一行日志。需要时再加 typed 方法 |
| `receive_reject_annotation` | **删除** | 同上 |
| `receive_exit_annotation` | **删除** | 同上 |
| `close_mupdf` | `session.close()` → `exit()` | 语义保持"有批注先问保存" |
| `mupdf_create_signature_row` | `session.createSignatureTable(names, ids)` | 原 `(List<String>) objects[0]` / `(List<Integer>) objects[1]` 的裸强转改为形参类型 |
| `receive_inform_signature` | `session.requestHostSignature()` | 加幂等：成员变量 `signatureRequestShowing`，已弹出则忽略（修 D6） |
| `submit_member_signature` | `session.submitMemberSignature(id, bytes)` | 原 `(int) msg.getObjects()[0]` 改形参 |

### 4.3 注册/注销的删除

| 位置 | 现在 | 改为 |
|---|---|---|
| `:646` `registerEventBus()` | 注册 EventBus | `startSession()`：构造 `MupdfSessionImpl`、注册进 `MupdfSessions`、调 `host.onSessionReady(session)` |
| `:2134` 密码通过后 `registerEventBus()` | 二次注册 | `startSession()`（同一个私有方法，位置正好对应"会话生效点"） |
| `:1735-1747` `registerEventBus` / `unregisterEventBus` | 两个方法 | **删除** |
| `:3555` `onDestroy` 里 `unregisterEventBus()` | 注销 | `stopSession(UNKNOWN)` 兜底（幂等） |

### 4.4 触摸事件节流

`dispatchTouchEvent`（`:271-277`）现在每次 `ACTION_DOWN` 都 post 一条消息走全局总线。改为：

```java
private long lastTouchNotifyMs;

@Override
public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
        long now = SystemClock.uptimeMillis();
        if (now - lastTouchNotifyMs >= 1000) {
            lastTouchNotifyMs = now;
            dispatchHost(MupdfHost::onUserTouched);
        }
    }
    return super.dispatchTouchEvent(ev);
}
```

> 需宿主确认这个回调的用途（超时锁屏？防截屏？）。若用途对时延敏感，节流窗口可调小，或改成"仅在会话开始后的首个 touch 上报一次"。

---

## 5. `MupdfMacro` 收编（P2，并存的前提）

### 5.1 字段去向

| 字段 | 库内读写情况 | 去向 | 理由 |
|---|---|---|---|
| `isSharing` | 只被 `reset()` 置 false，**库内从不置 true** | 迁移为会话内字段 `isSharingSession`，由 `MupdfSession` 内部维护 | 现在靠宿主持有引用写静态值，是 ④ 通道的核心 |
| `shareAnnotationEnable` | `:1178` 读取（控制"邀请"按钮） | `MupdfConfig.Builder.shareAnnotationEnabled()` | 静态配置 |
| `isHengXunVersion` | `:1094` 选布局 | `MupdfConfig.Builder.hengXunStyle()` | 静态配置 |
| `delete_history_annotation` | 库内**零读取** | `MupdfConfig.Builder.allowDeleteHistoryAnnotation()`，P2 时补上实际读取点 | 宿主在 example 里赋值但库不用 → 需确认是漏实现还是废弃 |
| `clarityLimitMode` | `:897` 写入；`PageView` 读 3 处（`:331/332/342`） | **删除静态**，`PageView` 改从传入参数取值 | 与 `MupdfConfig.getClarityLimitMode()` 双源（D2） |
| `backgroundColor` | `:898` 写入 + 设置对话框运行时改（`:3140/3173/3192/3339`）；`PageView` 读 5 处（`setBackgroundColor` 4 处：`:137/286/532/831`，`buildColorFilter()` 1 处：`:822`） | **删除静态**，改为 `PageView`/滤镜持有实例字段 | 并存下 A 的会话改掉 B 的显示（D3）；注意这一步要动 `PageView`，工作量比看起来大 |
| `brightness` | `:899` 写入 + `:3140` 运行时改 | **删除静态**，改为 Activity 实例字段 | 同上 |
| `currentMediaId` / `currentFilePath` / `currentUri` | 只在 `:900-902` 写入，库内**零读取** | 先确认宿主是否读取（若读取，它属于通道 ④ 的"库写宿主读"）；确认后再删，宿主改用 `session.id()` + 自己保存的入参 | 库内零读取 ≠ 无用，可能是"库写给宿主读"的单向通道（D11） |
| `sharingIds` / `launchSrcwbid` / `launchSrcmemid` | 库内**零读写** | **删除**（死字段 D11） | 需宿主确认是否在用 |
| `reset()` | `onDestroy` 调用 | **删除**，改由会话实例消亡自然清理 | 并存下 `reset()` 会清掉另一个会话的状态 |
| `DEFAULT_*` / `MIN_BRIGHTNESS` 等常量 | | **保留**（常量不是状态） | |
| `buildColorFilter()` | | 改为接收背景色参数 `buildColorFilter(int backgroundColor)` | 纯函数化 |
| `clampBrightness()` | | 保留 | 纯函数 |

### 5.2 收编后 `MupdfMacro` 的形态

```
MupdfMacro          → 只保留：常量（DEFAULT_*、MIN/MAX_BRIGHTNESS、bundle_key_*、TAG）
MupdfMacro 的静态可变状态 → 全部迁入 MupdfConfig 或会话实例
```

`bundle_key_*` 一堆常量建议在 P3 一并收敛：改为 `MupdfConfig` 内部私有常量（宿主不需要知道 Intent 的 key）。这是可选的额外收益。

### 5.3 这一步必须做吗？

**必须。** 若只删 EventBus 不动 `MupdfMacro`：
- D2/D3 仍然存在 → 并存下纸张色、亮度、清晰度互相干扰；
- 共享开关仍然靠宿主写库的静态布尔值 → ④ 通道原样保留，"去掉 EventBus"只是换了个地方继续全局耦合。

---

## 6. 生命周期与时序改造

### 6.1 会话起止点

| 事件 | 位置 | 动作 |
|---|---|---|
| `core` 就绪且密码通过 | 原 `:646` | `startSession()` → 注册路由表 → `host.onSessionReady(session)` |
| 密码通过（懒汉路径） | 原 `:2134` | 同上（幂等，已启动则跳过） |
| 配置变更重建 | `onRetainCustomNonConfigurationInstance` → 新 `onCreate` | 再次 `onSessionReady`，**sessionId 不变**，路由表里换实现实例 |
| 用户退出 / 保存完成 / 外部打开 / 打开失败 / 宿主 close | 各自分支 | `sessionEnded(reason)` |
| `onDestroy` | `:3540` | `sessionEnded(UNKNOWN)` 兜底（幂等） |

`sessionEnded(reason)` 的语义（**新增**，幂等）：

```
1. if (sessionEnded) return;
2. sessionEnded = true;
3. sessionActive = false;                       // 之后 dispatchHost / session 调用均为空操作
4. if (reason == SAVED) 已在 O12 里回调过 onUploadRequested
5. MupdfSessions.unregister(sessionId);
6. host.onSessionClosed(sessionId, reason);
7. host = null;                                 // 释放宿主引用
8. 若走 MupdfLauncher 路径：把 MupdfResult 写入 Activity 的 result Intent
```

### 6.2 四条关键时序（现在 → 目标）

**① 保存 → 上传 → 关闭**（`saveAndExit()`，`:2644-2665`）

现在：
```
后台线程 core.save() → post inform_upload（靠 MAIN 切回主线程）→ 延迟 2000ms finish()
```
问题：上传是否完成与 finish 无关；2000ms 是魔法数（等宿主跳页面）。

目标：
```
后台线程 core.save()
  → 成功：postToMain{ host.onUploadRequested(path, dirId); sessionEnded(SAVED); finish(); }
  → 失败：postToMain{ sessionEnded(SAVE_FAILED); 提示 + finish(); }
```
- 明确库**不等待**上传完成（与现语义一致），但把 `sessionEnded` 提前到 `finish()` 之前，让宿主有机会在上传回调里同步启动自己的页面跳转；
- 2000ms 延迟：改为**可配置项** `saveExitDelayMs(long)`，默认 2000 保持行为不变；若宿主在 `onUploadRequested` 里自己处理跳转，可设为 0。此项列入未决问题。

**② 外部打开**（`viewTopJump:1414-1427` / `showCannotOpenDialog:2072-2091`）

现在：`finish()` 先执行，再 post（D7）。
目标：`host.onOpenExternally(...)` → `sessionEnded(OPEN_EXTERNAL / OPEN_FAILED)` → `finish()`。

**③ 签名通知**（`:541-559`）

现在：收到就弹画板，无幂等（D6）。
目标：`requestHostSignature()` 检查 `signatureRequestShowing`，已在显示则忽略；画板关闭后复位。签名结果经 `onSignatureCaptured` 回调。

**④ 批注会话退出**（`:3546-3548`）

现在：`onDestroy` 里若 `isSharing` 则 post。
目标：在 `sessionEnded` 内、`host` 置 null **之前**回调 `onAnnotationSessionExited(mediaId)`（会话级，不属于 close 语义），并保证即使 `onDestroy` 兜底路径也能发出。

### 6.3 `onNewIntent` 语义（需依赖端确认）

`onNewIntent` → `recreate()`（`:651-661`），即"同一个 Activity 实例换了文档"。两种可选语义：

| 方案 | 语义 | 优点 |
|---|---|---|
| **A（建议）** | 旧会话 `sessionEnded(REPLACED)` + 新 sessionId 启动新会话 | 宿主网络层路由清晰，旧会话的入站不会打错文档 |
| B | 同一 sessionId，继续复用 | 宿主改动小，但"一个会话 = 一份文档"不再成立，路由表语义变模糊 |

走 A 时需在 `loadDocument()` 里比较 bundle 中的 `sessionId`，不同则先 `sessionEnded(REPLACED)`。

### 6.4 宿主页面重建（重要的坑）

宿主页面（发起方）因旋转/内存被重建时：
- 阅读器 Activity 可能仍然存活（它在上层），宿主持有的 `MupdfSession` 引用随页面销毁而丢失；
- 重建后的宿主页面用 ViewModel 里保存的 `sessionId` 调 `MupdfSessions.find(sessionId)` 重新拿句柄；
- 若返回 `null` → 会话已结束，宿主的网络层应停止投递。

这正是"页面级入站方 + 按并存设计"必然留下的唯一静态入口，**但它不是通信通道**：无广播、无按类型分发、必须显式 id、返回对象自带存活检查。

---

## 7. 宿主（依赖端）迁移清单

### 7.1 迁移前后对照

**现在：**

```java
// Application / MainActivity onCreate
EventBus.getDefault().register(this);

@Subscribe(threadMode = ThreadMode.MAIN)
public void eventBus(MupdfEventMessage msg) {
    switch (msg.getType()) {
        case MupdfBusType.out_open_inform:
            String path = (String) msg.getObjects()[0];
            String uri  = (String) msg.getObjects()[1];
            ...
        case MupdfBusType.inform_share_annotation:
            List<MupdfInkBean> ops = (List<MupdfInkBean>) msg.getObjects()[0];
            network.send(ops);
        ...
    }
}

// 投递远端批注
EventBus.getDefault().post(new MupdfEventMessage.Builder()
        .type(MupdfBusType.receive_annotation_info)
        .objects(remoteOps).build());

// 共享开关（通道 ④）
MupdfMacro.isSharing = true;
```

**目标：**

```java
// 1) 会话页持有句柄
private MupdfSession session;   // 或在 ViewModel 里存 sessionId

private final MupdfHost host = new MupdfHost() {
    @Override public void onSessionReady(MupdfSession s) {
        session = s;                       // 页面级入站入口
        viewModel.saveSessionId(s.id());
    }
    @Override public void onSessionClosed(String id, int reason) {
        session = null;                    // 必须清，否则泄漏
        viewModel.clearSessionId();
    }
    @Override public void onLocalOperations(List<MupdfInkBean> ops) { network.send(ops); }
    @Override public void onUploadRequested(String path, int dirId) { uploader.upload(path, dirId); }
    @Override public void onOpenExternally(String path, String uri) { WpsUtil.open(this, path, uri); }
    @Override public void onScreenshotRequested(String title) { ... }
    @Override public void onSignatureCaptured(byte[] bmp) { ... }
    // 未实现的方法保持 default 空实现即可
};

// 2) 打开
new MupdfLauncher().launch(new MupdfConfig.Builder()
        .sessionId(myGeneratedId)          // 并存路由键
        .host(host)
        .shareAnnotationEnabled(true)
        .hengXunStyle(false)
        .filePath(path)
        ...
        .build());

// 3) 投递远端批注（任意线程）
MupdfSession s = session;
if (s != null && s.isActive()) {
    s.receiveRemoteOperations(remoteOps);
}

// 4) 页面重建后重连
MupdfSession reclaimed = MupdfSessions.find(viewModel.sessionId());
```

### 7.2 宿主检查表

- [ ] 删除 `EventBus.getDefault().register/unregister` 相关代码与 `@Subscribe` 方法
- [ ] 删除 `org.greenrobot:eventbus` 依赖（若宿主别处不用）
- [ ] 所有 `MupdfMacro.xxx = ...` 赋值 → 迁到 `MupdfConfig.Builder`
- [ ] 所有 `MupdfBusType.*` 的 `switch` 分支 → 拆成 `MupdfHost` 的 override 方法
- [ ] 所有 `EventBus.post(new MupdfEventMessage(...))` → 改调 `session.xxx()`
- [ ] 生成并持久化 `sessionId`（建议 `URI + 时间戳` 或业务主键）
- [ ] 网络层投递前检查 `session != null && session.isActive()`
- [ ] 确认 `onUserTouched` 的真实用途，是否可接受 1s 节流
- [ ] 确认 `MupdfMacro.sharingIds / launchSrcwbid / launchSrcmemid` 是否被宿主使用（库内已零引用）
- [ ] 确认 `MupdfMacro.delete_history_annotation` 的预期行为（库内零读取，疑似未实现）

---

## 8. 依赖与打包（P3）

### 8.1 变更

| 项 | 现在 | 目标 |
|---|---|---|
| `mupdf/build.gradle:59` | `api 'org.greenrobot:eventbus:3.3.1'` | 删除该行 |
| `example/build.gradle:56` | `implementation("org.greenrobot:eventbus:3.3.1")` | 删除 |
| `bus/` 包 | `MupdfBus`、`MupdfBusType`、`MupdfEventMessage` | 删除 |
| `bus/` 包 | `MupdfInkBean`、`MupdfOperationId` | **移动**到 `api/` 包（它们是数据契约，不是总线） |
| 版本 | 6.0.42 | **7.0.0**（major，tag 走 `.github/workflows/jitpack-tag.yml`） |

### 8.2 fail-fast 策略

宿主若只升级库、没改代码，症状是"所有事件静默消失"，很难排查。P3 建议在入口做检查：

```java
public static void jump(Context context, MupdfConfig config) {
    if (config.getHost() == null && !config.isResultMode()) {
        throw new IllegalStateException(
            "MupdfConfig.host is null: 7.0.0 起已移除 EventBus，请实现 MupdfHost 或使用 MupdfLauncher");
    }
    ...
}
```

理由：编译期已经能抓住"宿主还在用已删除的 API"；但"升级了库却忘了注入 host"编译期抓不到，只能运行期 fail-fast。日志 + 文档 + CHANGELOG 三者都要有。

### 8.3 额外收益

- 宿主不再需要 EventBus 的 keep 规则（`@Subscribe` 反射扫描）；
- 库内部逻辑可用 fake `MupdfHost` 做 JVM 单测（当前 `:mupdf` 无任何测试依赖）；
- 依赖图更干净，宿主不再被迫感知 EventBus 版本。

---

## 9. 各阶段验收标准

| 阶段 | 验收 |
|---|---|
| **P1** | `api` 包编译通过；`example` 未改动仍可运行（旧路径不变）；接口 Javadoc 中的线程/生命周期契约在评审中逐条确认 |
| **P2** | `MuPdfDocumentActivity` 中 `MupdfBus\.` 与 `EventBus\.` 出现次数为 **0**；`MupdfMacro.` 只剩常量引用；13 个出站点与 8 个入站点逐一勾销；手工验证共享批注双端互通、签名表、截图、保存上传、外部打开五条链路 |
| **P3** | `org.greenrobot` 在整个工程 grep 为 0；`bus/` 包不存在；`assembleRelease` + `publishReleasePublicationToMavenLocal` 通过；版本号 7.0.0 |
| **P4** | `example` 重写为 `MupdfHost` + `MupdfLauncher` 示范（不再 register EventBus）；`docs/annotation-flow.md` 第 113/151/201/204 行的 EventBus 描述更新；`AGENTS.md`/`CLAUDE.md` 第 49/53/62 行更新；README 增补 6.x → 7.0 迁移指南 |

---

## 10. 风险与回退

| 风险 | 影响 | 缓解 |
|---|---|---|
| 宿主未同步升级，事件静默消失 | 高 | major 版本号 + CHANGELOG + `IllegalStateException` fail-fast；发布前与依赖端对齐升级窗口 |
| `MupdfHost` 实现持有 Activity 导致泄漏 | 中 | 契约要求随 `onSessionClosed` 释放；库在 `sessionEnded` 后置 null；`activeSessionIds()` 供排查 |
| 会话重建（旋转）语义理解错 | 中 | `onSessionReady` 可多次回调写进 Javadoc；example 示范按 sessionId 去重 |
| `MupdfMacro` 收编时漏掉某个读取点 → 行为回退 | 中 | 保留 `buildColorFilter(int)` 的可测纯函数形态；P2 逐条 grep 勾销 `MupdfMacro.` |
| 触摸回调用途不明，节流破坏了宿主逻辑 | 低 | 列入未决项，先向依赖端确认 |
| `onNewIntent` 语义选错 | 中 | 第 6.3 节两方案，先确认再动 |

回退方式：P1/P2 是纯新增 + 内部改造，`jump(Context, Bundle)` 旧入口在 P2 阶段仍保留 → 可随时回退到 6.x 分支；破坏性只发生在 P3。

---

## 11. 未决项（需依赖端确认后再定稿）

1. **`onUserTouched` 的真实用途**：若是"无操作超时锁定"，1s 节流是否有影响？是否应改为"仅在会话首个 touch 上报"？
2. **`onNewIntent` 语义**：第 6.3 节的 A（新会话）还是 B（复用会话）？
3. **`saveExitDelayMs`**：保留 2000ms 魔法数（默认行为不变）还是由宿主在上传回调里自行跳转（设为 0）？
4. **`MupdfMacro.sharingIds / launchSrcwbid / launchSrcmemid` 是否被宿主使用？** 库内已零读写，倾向删除。同理 `MupdfMacro.currentMediaId / currentFilePath / currentUri`（库内只写不读，疑似"库写给宿主读"的单向通道）删除前必须确认。
5. **`MupdfMacro.delete_history_annotation`**：库内零读取，是漏实现还是已废弃？
6. **宿主的网络层是否能拿到"当前阅读页"的引用**：若实际是 Application 级单例，第 7.1 节的 `onSessionReady` 持有方式需改为"页面注册到宿主自己的 holder"，`MupdfSessions.find` 的使用频率会显著上升。
