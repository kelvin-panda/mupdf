package com.xlk.mupdf.library.api;

import java.util.List;

/**
 * 一次文档阅读会话的句柄（宿主 → 库）。
 *
 * <p>替代原先"宿主 post 到全局 EventBus、所有阅读器实例都会收到"的广播语义：
 * 一个 {@link MupdfSession} 只指向唯一的阅读器实例，因此进程内并存多个阅读会话时不会串台。</p>
 *
 * <h3>线程契约</h3>
 * <p>本接口所有方法都可以从<b>任意线程</b>调用，库内部会切到主线程处理。</p>
 *
 * <h3>生命周期契约</h3>
 * <ul>
 *   <li>仅在 {@link MupdfHost#onSessionReady(MupdfSession)} 之后、
 *       {@link MupdfHost#onSessionClosed(String, int)} 之前有效；</li>
 *   <li>结束后再调用任何方法都是<b>安全的空操作</b>（不抛异常，也不静默丢数据）；</li>
 *   <li>{@link #isActive()} 可从任意线程读取。</li>
 * </ul>
 *
 * <p>获取方式：{@link MupdfHost#onSessionReady(MupdfSession)} 回调，
 * 或 {@link MupdfSessions#find(String)} 按 id 查回。</p>
 *
 * @author : Administrator
 * @since 7.0.0
 */
public interface MupdfSession {

    /**
     * 会话标识，来自 {@code MupdfConfig.Builder#sessionId(String)}。
     *
     * <p>宿主 Activity 重建后本值不变，因此可以安全地用作宿主侧的路由键。</p>
     */
    String id();

    /**
     * 是否仍可投递指令。
     *
     * <p>宿主网络层在投递前应先检查；返回 {@code false} 表示会话已结束。</p>
     */
    boolean isActive();

    /**
     * 当前会话对应的文件媒体 id（来自 {@code MupdfConfig#getMediaId()}，未设置时为 0）。
     *
     * <p>替代原先读 {@code MupdfMacro.currentMediaId} 的做法：后者是进程级单值，
     * 并存会话或会话刚结束时都会给出错误答案。</p>
     */
    int mediaId();

    /**
     * 当前会话是否正在阅读指定媒体文件。
     *
     * <p>宿主在"收到某个文件的批注通知，判断要不要先打开文件"时应该用本方法，
     * 而不要读静态字段。</p>
     *
     * @param mediaId 文件媒体 id
     */
    boolean hasDocument(int mediaId);

    /**
     * 投递远端批注操作（原 {@code MupdfBusType#receive_annotation_info}）。
     *
     * <p>{@code operationId} 相同的操作只会生效一次；{@code operationId <= 0} 的操作
     * 由库内部补号。传 {@code null} 或空列表是安全的。</p>
     *
     * @param operations 远端操作，允许 ADD / ERASE / UNDO 混合
     */
    void receiveRemoteOperations(List<MupdfInkBean> operations);

    /**
     * 创建签名表（原 {@code MupdfBusType#mupdf_create_signature_row}）。
     *
     * <p>重复调用以首次为准，不会重复建表。</p>
     *
     * @param memberNames 成员姓名，顺序决定表格行序
     * @param memberIds   成员 id，与 {@code memberNames} 一一对应
     */
    void createSignatureTable(List<String> memberNames, List<Integer> memberIds);

    /**
     * 提交某成员的签名位图（原 {@code MupdfBusType#submit_member_signature}）。
     *
     * @param memberId 成员 id，须出现在 {@link #createSignatureTable} 的名单里
     * @param bmpBytes BMP 字节数据
     */
    void submitMemberSignature(int memberId, byte[] bmpBytes);

    /**
     * 请求本机弹出签名画板（原 {@code MupdfBusType#receive_inform_signature}）。
     *
     * <p>库内保证同一时刻只会有一个签名画板，重复调用会被忽略（原实现会重复弹窗）。</p>
     */
    void requestHostSignature();

    /**
     * 请求关闭阅读器（原 {@code MupdfBusType#close_mupdf}）。
     *
     * <p>语义与用户按返回键一致：存在未保存批注时仍会弹保存提示。
     * 若宿主需要"不提示直接关闭"，请等后续版本提供的强制关闭接口。</p>
     */
    void close();
}
