package com.xlk.mupdf.library.api;

import java.util.List;

/**
 * 库向宿主提供的能力端口（库 → 宿主）。
 *
 * <p>替代原先基于 EventBus 的 {@code MupdfBusType} 广播：宿主实现本接口并通过
 * {@code MupdfConfig.Builder#host(MupdfHost)} 注入，库直接回调，不再依赖任何事件总线。</p>
 *
 * <h3>线程契约</h3>
 * <p>本接口所有方法均在<b>主线程</b>回调。</p>
 *
 * <h3>生命周期契约</h3>
 * <ul>
 *   <li>在 {@link #onSessionReady(MupdfSession)} 与 {@link #onSessionClosed(String, int)} 之间有效；</li>
 *   <li>{@code onSessionClosed} 之后库不再持有本对象引用；</li>
 *   <li>{@code onSessionClosed} 保证在阅读 Activity 销毁前回调，且只回调一次。</li>
 * </ul>
 *
 * <h3>向后兼容契约</h3>
 * <p>所有方法都是 {@code default} 空实现：库版本升级新增回调时，宿主的既有实现<b>不会</b>编译失败。</p>
 *
 * @author : Administrator
 * @since 7.0.0
 */
public interface MupdfHost {

    /**
     * 会话就绪：文档已解析、渲染引擎可用，可以开始接收入站指令。
     *
     * <p><b>可能被回调多次</b>：宿主 Activity 重建（旋转屏幕、{@code onNewIntent} 触发的
     * {@code recreate()} 等）后会以<b>相同 sessionId</b> 再次回调，并给出新的
     * {@link MupdfSession} 句柄。宿主应按 sessionId 去重，不要假定只回调一次。</p>
     *
     * <p>若宿主已丢失句柄（例如发起方页面被重建），可用
     * {@link MupdfSessions#find(String)} 按 sessionId 重新获取。</p>
     *
     * @param session 本次会话的定向句柄
     */
    default void onSessionReady(MupdfSession session) {
    }

    /**
     * 会话结束。
     *
     * <p>宿主应在此释放 {@link MupdfSession} 引用、停止向该会话投递指令。
     * 之后对 session 的任何调用都是安全的空操作。</p>
     *
     * @param sessionId 会话标识
     * @param reason    结束原因，见 {@link MupdfExitReason}
     */
    default void onSessionClosed(String sessionId, @MupdfExitReason int reason) {
    }

    //<editor-fold desc="原先的 EventBus 出站事件">

    /**
     * 本地产生了批注操作（原 {@code MupdfBusType#inform_share_annotation}）。
     *
     * <p>载荷可能是 {@link MupdfInkBean#OP_ADD}、{@link MupdfInkBean#OP_ERASE}
     * 或 {@link MupdfInkBean#OP_UNDO}，宿主应原样转发给其它端。</p>
     *
     * <p><b>注意</b>：库不再自行判断"当前是否处于共享批注中"，是否转发由宿主决定
     * （非共享状态下忽略本回调即可）。</p>
     *
     * @param operations 产生顺序不可保证，但同一 {@code operationId} 不会重复
     */
    default void onLocalOperations(List<MupdfInkBean> operations) {
    }

    /**
     * 批注已保存成功，请宿主上传（原 {@code MupdfBusType#inform_upload}）。
     *
     * <p>库<b>不会</b>等待上传结果，回调后按 {@code saveExitDelayMs} 自行关闭阅读器。
     * 若宿主希望自行决定关闭时机，把该值设为 0 并在本回调里完成跳转。</p>
     *
     * @param savedPath 保存后的文件绝对路径
     * @param uploadDirId 上传目录 id，来自 {@code MupdfConfig#getUploadDirId()}
     */
    default void onUploadRequested(String savedPath, int uploadDirId) {
    }

    /**
     * 用户点了截图（原 {@code MupdfBusType#inform_screenshot}）。
     *
     * <p>原事件第二个参数恒为 0，已移除。</p>
     *
     * @param docTitle 文档标题
     */
    default void onScreenshotRequested(String docTitle) {
    }

    /**
     * 需要宿主用外部应用打开该文件（原 {@code MupdfBusType#out_open_inform}）。
     *
     * <p>回调后库会紧接着回调 {@link #onSessionClosed(String, int)} 并关闭阅读器。</p>
     *
     * @param filePath 文件绝对路径，优先使用
     * @param uri      content uri，仅在 filePath 为空时作为兜底
     */
    default void onOpenExternally(String filePath, String uri) {
    }

    /**
     * 邀请他人参与批注（原 {@code MupdfBusType#inform_invite_annotation}）。
     *
     * @param docTitle   文档标题
     * @param mediaId    文件媒体 id
     * @param pageNumber 页码，索引 + 1
     */
    default void onAnnotationInviteRequested(String docTitle, int mediaId, int pageNumber) {
    }

    /**
     * 本机退出了批注会话（原 {@code MupdfBusType#inform_exit_annotation}）。
     *
     * <p>每次会话结束都会回调，由宿主判断"当前是否真的处于共享批注中"再决定是否发协议。</p>
     *
     * @param mediaId 文件媒体 id
     */
    default void onAnnotationSessionExited(int mediaId) {
    }

    /**
     * 秘书端请求他人签名（原 {@code MupdfBusType#inform_inform_signature}）。
     *
     * @param mediaId 文件媒体 id
     */
    default void onSignatureInviteRequested(int mediaId) {
    }

    /**
     * 本机签名采集完成，交宿主回传（原 {@code MupdfBusType#result_signature}）。
     *
     * @param bmpBytes BMP 字节数据
     */
    default void onSignatureCaptured(byte[] bmpBytes) {
    }

    /**
     * 用户在阅读器上有触摸（原 {@code MupdfBusType#inform_touch_device}）。
     *
     * <p>库不节流：每次触摸（{@code ACTION_DOWN}）都会回调。需要节流或边沿触发的宿主
     * 自行判断（例如"仅在悬浮控件处于隐藏状态时唤醒一次"）。</p>
     */
    default void onUserTouched() {
    }

    /**
     * 签名表内所有成员都已提交签名（新增）。
     *
     * <p>原实现由宿主自行计数，容易与库内状态不一致；现在由库判断并回调。
     * 若宿主不需要，不实现即可。</p>
     */
    default void onSignatureTableCompleted() {
    }
    //</editor-fold>
}
