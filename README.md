
[![](https://jitpack.io/v/com.github.kelvin-panda/mupdf.svg)](https://jitpack.io/#com.github.kelvin-panda/mupdf)
# 更新
## 7.0.12
1. **修复「共享批注时对端一操作，本机就闪屏刷新」**
   - 原因：远端擦除 / 撤销没有笔迹包围盒，`refreshAfterSharedChanges()` 只能退回
     `afterAnnotationPreservingScroll()` → `ReaderView#afterAnnotation()`，而它会
     **移除并重建所有子 View**（`mAdapter.refresh()` + `mViewCache.clear()` + `requestLayout()` + `invalidate()`），
     整屏重绘一次 → 肉眼就是闪一下。远端新增走的是 `updatePatch()` 局部重绘，所以不闪
   - 现在 `MuPDFCore#deleteAnnotationsByOperationIds(...)` 会通过出参 `List<DeletedBounds>` 返回
     被删批注的包围盒（PDF 点），宿主把它换算成页内显示像素并入 `sharedPatchRects`，
     远端擦除 / 撤销也走 `updatePatch()` 局部重绘
   - 即便个别情况下拿不到包围盒，也只重绘当前页的 `PageView`（`pv.update()`），不再整屏重建
2. 新增日志：`远端操作：擦除 … 包围盒=N`、`共享批注当前页更新（无包围盒），按页重绘`
## 7.0.11（已发布 tag）
本次发布把 **7.0.5 ~ 7.0.10 的全部修复**合并成一个可依赖的版本（这几个版本只在本地产出过、没有单独打 tag），
升级到 7.0.11 即等于拿到下面所有内容：

1. **取消批注（`viewArtClose`）会把本机的丢弃同步给参与人**
   - 本次会话内本机新增的每一笔都生成 `OP_UNDO` 交给宿主 → `(PDFWHITEBOARD, DEL)`，对端同步删除；
     本地那一笔即使已不存在（会话内被自己擦过）**也照发**，避免对端残留
   - 修掉"多笔落在同一页时部分撤销不下发"（原用变更页集合 `size` 判断，同页第二笔起被误判为没改动）
   - 修掉取消时"按页删最后一条"多删一笔（会误删对端后到的笔）：`deleteLastAnnotation` 跳过他人批注
2. **修复"删除看起来执行了、但页面上那条笔永远在"**
   - 页面被换出重载后 MuPDF 会重建 native 批注对象，旧 `PDFAnnotation` 句柄调 `deleteAnnotation()`
     是**静默空操作**。现在批注写入 `/Contents = xlkop:<operationId>`，删除前先
     `refreshPageRegistrations()` 用当前页面对象重建登记，再删；本地擦除/撤销同样先重建
3. **修复批注笔迹错位 / 落到隔壁页**（横屏设备典型）
   - 页高不再退回"屏幕高度"，统一按 `页宽 × 页面宽高比` 校验修正（新增 `MuPDFCore#getPageSizeOf`）
   - `ReaderView#getPageHeight()` 也收口为"页宽 × 页面宽高比"，页顶坐标、`findPageAtY`、按页切分全部自洽；
     否则页高失真会让一笔跨多个"虚拟页"，被切成几十条 `pageindex` 乱跳的消息，对端画到相邻页
4. 共享批注刷新优化：当前页远端笔迹按笔迹包围盒局部重绘（`sharedPatchRects`），避免整页重绘闪屏
5. 排障日志：`[批注删除] operationId=… 删除=N 剩余=M`（`剩余` 是删完后该页真实批注条数，
   用来区分"没删掉"与"删了没刷新"）、`批注页高与宽高比不符，按宽高比修正`
## 7.0.10
1. **修复「A 端第 1 页的批注出现在 B 端第 2 页」，以及一笔被拆成几十条分段消息**
   - 原因：`ReaderView#getPageHeight()` 优先用子 View 的测得高度，测得值 / `mScale` 与页宽不一致时，
     整份文档的页顶坐标失真（实测页高被算成 1200，实际 2830）。一笔的 Y 范围因此跨了多个"虚拟页"，
     `splitInkByPages` 把它切成 `pageindex=2 / 3` 交替的几十条消息，对端就在相邻页各画一堆碎片
   - 现在页高统一由 `页宽 × 页面宽高比` 推导（页面尺寸取自 `PageAdapter#getPageSizeSync` 缓存），
     页宽、页高、页顶坐标、`findPageAtY` 全部自洽
## 7.0.9
1. **修复「批注笔迹整体偏移（横屏设备上偏下）」**
   - 原因：提交批注时用 `ReaderView#getPageDisplayHeight()` 取页面显示高度，拿不到（返回 0）就退回
     **屏幕高度**。横屏设备（屏幕 2000×1200）上页面是竖版（显示 1999×2830），屏幕高度与页高完全不成比例，
     纵坐标按错误比例换算（实测被缩小 2.6 倍）→ 笔迹整体下移。绘制预览走的是另一套几何，所以只有
     **落笔后的批注**错位
   - 现在 `getAnnotationPageHeight()` 按 `页宽 × 页面宽高比`（真实页尺寸由 `MuPDFCore#getPageSizeOf` 提供）
     校验并修正页高，不符时打日志 `批注页高与宽高比不符，按宽高比修正`；页宽兜底也改为按页面缩放比例换算，
     不再用屏幕宽度
2. `MuPDFCore#getPageSizeOf(int)`：读取指定页真实尺寸（PDF 点），不影响当前渲染页
## 7.0.8
1. **修复「删除一条远端笔迹看起来执行了、但页面上那条笔永远在」**（表现为退出后对端残留某一笔）
   - 原因：`MuPDFCore` 用 `Map<PDFAnnotation, …>` 保存"操作 → 批注对象"。页面被换出重载后
     （`invalidatePageCache()`、翻页渲染都会触发），MuPDF 会把页面批注**重建为一组新的 native 对象**，
     旧句柄随即失效；拿它调用 `pdfPage.deleteAnnotation()` 是**静默空操作**——不报错、也不删。
     而登记关系同时被移除，于是那条批注再也删不掉，屏幕上一直留着
   - 现在给批注写入 `/Contents = xlkop:<operationId>`，删除前先 `refreshPageRegistrations()`
     用**当前页面**的批注对象重建登记，再执行删除；本地擦除 / 撤销
     （`deleteAnnotationWithOperationIds`、`deleteLastAnnotation`）同样先重建，
     保证"他人批注只能他人删除"的判断不会因句柄失效而失效
2. 删除路径补数据级对账日志：`[批注删除] operationId=… 删除=N 剩余=M`
   （`剩余` 是删完后该页真实批注条数，用来区分"没删掉"与"删了没刷新"）
## 7.0.6
1. **修复「取消批注（不保存）后两端各自只剩对方的笔迹」**：`viewArtClose` 现在会把自己的丢弃同步给参与人
   - 取消批注时，本次会话内**本机新增**的每一笔都会生成一条 `MupdfInkBean.OP_UNDO` 交给宿主
     （宿主转成 `(PDFWHITEBOARD, DEL)`），对端据此删除本机这次画的笔
   - 即使本地那一笔已经不在了（本次会话里又被自己擦掉过），这条撤销**仍会下发**，保证对端不残留
   - 本次会话里擦掉的"历史笔"按取消语义还原（`markLocallyErasable`，作者仍可再擦）；
     擦掉的"本会话自己的笔"直接随第一步整体删除，不再"先还原再删除"
2. **修复取消 / 撤销多删一笔**：`MuPDFCore.deleteLastAnnotation` 跳过共享来源（别人画的）批注，
   只删"本机可删的最后一条"（原实现直接删数组最后一条，对端的笔迹后到时会被误删）
3. **修复"多笔落在同一页时部分撤销不下发"**：改用 `applyUndoOperation` 的返回值判断是否真的改动了页面，
   不再用变更页集合的 `size` 变化（同一页上的第二笔 `size` 不变，会被误判为"没改动"而不同步）
4. 取消批注的兜底逻辑（无 operationId 的历史批注按页删最后一笔）只作用于"没有被撤回逻辑处理过"的页
## 7.0.5
1. 共享批注刷新优化：当前页收到远端笔迹时按**笔迹包围盒局部重绘**（`sharedPatchRects`），
   批注中保持实时刷新，避免整页重绘导致的闪屏
## 7.0.4
1. 修复「概率性擦不掉自己的笔画」：本地擦除的**命中测试**先排除共享来源（别人画的）批注
   - 原因：两者位置重叠时 `findShouldDeleteAnnotation` 会一直命中别人的笔而被拦，
     同一位置下自己的笔永远轮不到
   - 现在橡皮只考虑可擦除的批注；别人的笔不再参与命中
## 7.0.3
1. **业务规则：别人画的批注只能别人删除**
   - 共享通道写入的批注（`MuPDFCore.addSharedAnnotations`）登记为"共享来源"，
     本地擦除（`deleteAnnotationWithOperationIds`）不再命中它们，直接返回空结果并打日志
   - 避免"本地擦掉、其它端仍在、保存后分叉"
   - 远端下发删除 / 撤销（`deleteAnnotationsByOperationIds`、`applyRemoteOperation`）不受影响
   - 本地"撤销擦除"恢复出来的笔通过 `MuPDFCore.markLocallyErasable` 恢复可擦除属性，
     保证作者仍能删掉自己的内容
## 7.0.2
1. 媒体 id 为 0 时「通知签名」入口**不显示**，点击也不再下发（加了 `Toast` 提示 `mupdf_signature_need_media_id`）
   - 原因：签名通知依赖 `fileid` 定位文件，`mediaId == 0` 时对端无法处理
2. 补充 `mupdf_signature_need_media_id` 中英文案
## 7.0.0（破坏性更新）
1. **移除 EventBus**：宿主与库之间改为「端口 + 会话句柄」
   - 宿主实现 `api/MupdfHost`（全部方法 `default`，按需覆写），通过 `MupdfConfig.Builder.host(...)` 注入
   - 库通过 `api/MupdfSession` 句柄下发指令（`receiveRemoteOperations` / `createSignatureTable` / `submitMemberSignature` / `requestHostSignature` / `close`）
   - 删除 `bus/MupdfBus`、`bus/MupdfBusType`、`bus/MupdfEventMessage`，`org.greenrobot:eventbus` 不再作为传递依赖
   - ⚠️ `host` 为 null 时 `jump()` 抛 `IllegalStateException`（fail-fast，避免回调静默丢失）
2. **会话定向路由**：新增 `MupdfConfig.Builder.sessionId(...)`；`MupdfSessions.find(id)` 是唯一静态入口（按显式 id 查询，无广播），进程内多个阅读器不再串台
3. **配置收编**：`MupdfConfig` 新增 `shareAnnotationEnabled(...)` / `hengXunStyle(...)`，替代 `MupdfMacro.shareAnnotationEnable` / `MupdfMacro.isHengXunVersion`
4. **移除 `MupdfMacro` 的会话级静态状态**：`isSharing` / `sharingIds` / `launchSrcwbid` / `launchSrcmemid` / `currentMediaId` / `currentFilePath` / `currentUri` 及 `reset()` 全部删除
   - 这些字段描述的是**宿主侧的会议会话**，所有权归还宿主；库内需要的信息改用 `MupdfSession#mediaId()` / `hasDocument(int)`
   - 修复：库退出不再顺手清掉宿主状态（原 `onDestroy` → `reset()` 会导致共享批注被意外中断）
5. **数据类迁移**：`bus/MupdfInkBean`、`bus/MupdfOperationId` → `api/`（`MupdfInkBean` 的包名变了，需要改 import）
6. 新增 `api/MupdfExitReason`（会话结束原因）、`api/MupdfResult`（终态快照）
7. 库不再判断"是否处于共享中"，`onLocalOperations` / `onAnnotationSessionExited` **一律回调**，是否发协议由宿主决定
8. 新增 `MupdfHost#onSignatureTableCompleted()`：签名全部到齐由库判断并回调（替代宿主自行计数）
9. 修复 `submit_member_signature` 成员不在名单时 `remove(indexOf(...))` 抛 `IndexOutOfBoundsException`
10. 修复「外部打开 / 打开失败」先 `finish()` 再回调的顺序问题（原来依赖 EventBus 同步分发才没丢）
11. 签名通知加幂等（重复通知不再重复弹签名画板）
12. 恒迅版布局补齐 `viewInformSignature` 控件（签名通知入口）
## 6.0.43（未发布）
1. 批注提交改为**局部 patch 重绘**：只重绘笔迹包围盒（实测 3–8ms，原整页重绘约 160ms），
   页面内容区与整页重绘逐像素一致；撤销/共享等其它刷新路径仍走整页重绘
2. 库版本号改为单一来源：由 `mupdf/build.gradle` 的 `jitpackVersion`（JitPack `VERSION` 环境变量 / 本地兜底值）注入为
   `R.string.mupdf_library_version`，启动日志不再硬编码
## 6.0.42
1. 修复批注绘制出现"两条线"：即时保存后同一笔不再同时被浮层和页面各渲染一份，只保留实时预览并在页面渲染完成后撤销
   - 浮层已提交笔画按滚动差平移，随文档（含双指平移/页面重排）一起移动，不再与 PDF 批注错开
   - 落笔、双指接管、进入批注三个时机停止惯性/翻页滚动动画，避免批注期间文档继续滑动导致坐标基准漂移
2. 修复绘制后闪屏：预览线宽改为按页面比例换算成与 PDF 批注相同的屏幕宽度，交接不再粗细跳变
## 6.0.41
1. 批注改为**即时保存**：每笔松开即写入 PDF 标注对象
2. 撤销/擦除/共享统一重构为基于 `operationId` 的操作流
   - 新增 `MupdfOperationId`，`MupdfInkBean` 支持 `OP_ADD / OP_ERASE / OP_UNDO` 与百分比坐标分段
   - 移除 `MupdfAnnotationBean`，`MuPDFCore` 增加按 operationId 注册/删除批注的能力
## 6.0.40
1. 适配预览中实时切换新的PDF文件
2. 新增`shouldSignature`配置参数：`=true`时PDF文件打开后展示签名框进行PDF的文件签名
3. 优化`ArtBoardDialog`，当`cancelable`为`false`时隐藏掉取消按钮
## 6.0.39
1. 修改签名Bitmap的获取方式
## 6.0.37
1. example 改为使用本地 `project(":mupdf")` 依赖，避免 JitPack 构建时引用尚未发布的远程版本
## 6.0.36
1. 添加签名提交的功能（测试）
## 6.0.35
1. 发布源迁移到 GitHub，坐标改为 `com.github.kelvin-panda:mupdf`
## 6.0.28
1. Activity添加`dispatchTouchEvent`拦截`MotionEvent.ACTION_DOWN`事件，通知给前端使用
2. `MupdfConfig`添加`informSignature`开关，用于秘书端通知参会端签名（未完善）
## 6.0.27
1. 界面右下角添加固定的返回控件
   - 可在`MupdfConfig`配置`backButtonEnabled`，默认`false`隐藏
## 6.0.26
1. 修复下划线删除线无法被擦除的问题
2. 修复多批注同一位置擦除崩溃问题
3. 改用`mWindowWatermarkEnabled`控制水印的显示与隐藏
## 6.0.25
1. 添加外部可控显示或隐藏的功能开关
   - 顶部菜单可显示或隐藏：水印、签名表、填写签名、设置里的水印（待完善使用场景）
   - 批注中的文本功能：退出批注时无法撤销和删除（待完善）
2. UI更新
   - 恒迅版本的缩略图图标
   - 退出时的保存提示：字体增大与调整间距
## 6.0.24
1. 适配再上线上传功能批注保存时文件名去除日期，交给外部处理
   - MuPDFCore 中 save 方法文件名去除日期与批注
2. 去除无效控件
   - 存在退出时批注保存的提示，`viewTopSave`控件无用（预留未删除）
3. 水印功能完全手动
   - 传递过来的水印内容需要用户手动设置
## 6.0.18
  - 依赖库`AndroidAutoSize`，让`MuPdfDocumentActivity`实现`CancelAdapt`。临时更新解决调用方使用此库导致的界面UI错乱的问题
## 6.0.17
  - 限制频繁点击刷新
## 6.0.16
  - 定制版本外部打开图标改成wps
  - 魔法值修复
## 6.0.15
  - 调整画笔工具关闭的图标位置到最后
## 6.0.14
  - 美化和修改图标，添加英文环境
## 6.0.13
  - 批注和签名后恢复原位置,添加参数`isSimulating`拦截响应`onFling`事件，避免模拟拖动导致的甩动效果
## 6.0.12
  - 批注和签名后恢复原位置
## 6.0.11
  - 签名添加画笔颜色自选功能
  - 打开后延迟加载一次界面，预防首次加载时模糊的情况
## 6.0.10
  - 添加功能参数`MupdfConfig.clarityLimitMode`用于限制清晰度以提高加载速度，默认不限制
  - 添加功能参数`MupdfConfig.fullScreenEnable`用于决定是否全屏缩放，默认全屏
## 6.0.9
  - 添加了`arm64-v8a`架构的so库
## 6.0.8
  - 批注后的文件名只添加年月日的标识，去掉后面的时分秒
  - 添加文件名递增相关方法
## 6.0.7
  - `MupdfConfig`添加功能开关相关参数:`wpsOpenEnable`
## 6.0.6
  - `MupdfConfig`添加功能开关相关参数:`annotationEnable`,`signatureEnable`,`captureEnable`
## 6.0.5
  - 添加参数`MupdfConfig.annotationSaveDirPath`，定义批注后保存的目录路径
## 6.0.4
  - 颜色选择器进行修改，保证独立，避免引用方布局文件与库中布局文件同名
## 6.0.3
  - 优化颜色选择器，主要添加颜色透明度的功能

# 使用

**步骤1**
Add it in your `settings.gradle.kts` at the end of repositories:

```
	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
```
**步骤2**

```
	dependencies {
	        implementation("com.github.kelvin-panda:mupdf:7.0.12")
	}
```
**依赖方module**
```
android{
    //... ...
    
    //解决API 28只创建arm64目录，导致找不到库的问题
    splits {
        abi {
            enable true
            reset()
            include 'armeabi-v7a','arm64-v8a'
            universalApk false
        }
    }
    packagingOptions {
        resources.pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        resources.pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
    }
}
```

**使用**

> 7.0.0 起必须注入 `MupdfHost`；不注入会抛 `IllegalStateException`。
> 只需要实现用得到的回调，其余保持接口的 `default` 空实现即可。

```java
// 1) 实现端口（建议用 Application / 长生命周期对象，不要在每次打开时 new）
public class PdfHost implements MupdfHost {
    private MupdfSession session;

    @Override public void onSessionReady(MupdfSession session) {
        this.session = session;                 // 之后所有入站指令都走它
    }
    @Override public void onSessionClosed(String sessionId, int reason) {
        this.session = null;                    // 必须清，否则泄漏
    }
    @Override public void onUploadRequested(String savedPath, int uploadDirId) {
        // 批注保存完成，交给自己的上传逻辑
    }
    @Override public void onLocalOperations(List<MupdfInkBean> ops) {
        // 本地批注操作：是否转发给其它端由这里决定
    }
    @Override public void onOpenExternally(String filePath, String uri) {
        // 用外部应用打开
    }
}

// 2) 打开
private void openMupdf(Uri uri) {
    MupdfConfig mupdfConfig = new MupdfConfig.Builder()
            .fileUri(uri.toString())        // 文件 uri
            .sessionId("pdf-" + System.currentTimeMillis())   // 会话标识（并存路由键）
            .host(pdfHost)                  // 必填
            .build();
    MuPdfDocumentActivity.jump(this, mupdfConfig);
}

// 3) 投递远端批注（任意线程）
MupdfSession s = pdfHost.session;
if (s != null && s.isActive()) {
    s.receiveRemoteOperations(remoteOps);
}
```

# 从 6.x 迁移到 7.0.0

7.0.0 是**破坏性**版本：EventBus 相关 API 全部删除，升级后旧代码无法编译（这是有意的，避免静默失效）。

| 6.x | 7.0.0 |
|---|---|
| `EventBus.getDefault().register(this)` + `@Subscribe(MupdfEventMessage)` | `implements MupdfHost`，通过 `MupdfConfig.Builder.host(...)` 注入 |
| `MupdfBus.post(MupdfBusType.receive_annotation_info, ops)` | `session.receiveRemoteOperations(ops)` |
| `MupdfBus.post(MupdfBusType.close_mupdf)` | `session.close()` |
| `MupdfBus.post(MupdfBusType.mupdf_create_signature_row, names, ids)` | `session.createSignatureTable(names, ids)` |
| `MupdfBus.post(MupdfBusType.submit_member_signature, id, bmp)` | `session.submitMemberSignature(id, bmp)` |
| `MupdfBus.post(MupdfBusType.receive_inform_signature)` | `session.requestHostSignature()` |
| `MupdfMacro.isSharing` | 由宿主自己维护（库总是回调 `onLocalOperations`） |
| `MupdfMacro.currentMediaId` | `MupdfSession#mediaId()` / `hasDocument(int)` |
| `MupdfMacro.isHengXunVersion` | `MupdfConfig.Builder.hengXunStyle(true)` |
| `MupdfMacro.shareAnnotationEnable` | `MupdfConfig.Builder.shareAnnotationEnabled(true)` |
| `MupdfMacro.reset()` | 删除；由宿主在会话结束时自行清理 |
| `com.xlk.mupdf.library.bus.MupdfInkBean` | `com.xlk.mupdf.library.api.MupdfInkBean` |

**升级检查清单**

- [ ] 删除所有 `EventBus.default().register/unregister` 与 `@Subscribe` 方法
- [ ] 删除对 `MupdfBus` / `MupdfBusType` / `MupdfEventMessage` 的引用（编译期会报错）
- [ ] 所有 `MupdfMacro.*` 赋值改为 `MupdfConfig.Builder`（或宿主自己的状态类）
- [ ] 所有 `jump()` 调用点补 `.sessionId(...)` 与 `.host(...)`
- [ ] `MupdfInkBean` 的 import 从 `bus.*` 改为 `api.*`
- [ ] 若宿主自身也在用 EventBus，可保留自己的依赖（库不再传递它）

**示例图**

![](https://raw.githubusercontent.com/kelvin-panda/mupdf/master/screenshot/1.png)



![](https://raw.githubusercontent.com/kelvin-panda/mupdf/master/screenshot/2.png)
