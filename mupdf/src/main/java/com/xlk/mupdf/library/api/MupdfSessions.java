package com.xlk.mupdf.library.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话路由表。
 *
 * <p><b>这不是通信通道</b>：没有广播、不按类型分发，只能按显式 {@code sessionId}
 * 取到唯一句柄，且返回对象自带 {@link MupdfSession#isActive()} 存活检查。</p>
 *
 * <p>典型用途：宿主发起方页面（Activity/Fragment）因配置变更被重建后，
 * 用保存在 ViewModel 里的 sessionId 重新拿到句柄。</p>
 *
 * <p>若宿主一直接持有 {@link MupdfSession}（例如由长生命周期的 Service 持有），
 * 则完全不需要用到本类。</p>
 *
 * @author : Administrator
 * @since 7.0.0
 */
public final class MupdfSessions {

    private static final ConcurrentHashMap<String, MupdfSession> SESSIONS = new ConcurrentHashMap<>();

    private MupdfSessions() {
    }

    /**
     * 按 id 查会话。
     *
     * @param sessionId 会话标识
     * @return 会话句柄；不存在或已结束返回 {@code null}
     */
    @Nullable
    public static MupdfSession find(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        MupdfSession session = SESSIONS.get(sessionId);
        if (session == null) {
            return null;
        }
        if (!session.isActive()) {
            SESSIONS.remove(sessionId, session);
            return null;
        }
        return session;
    }

    /**
     * 当前进程内仍存活的会话 id。
     *
     * <p>用于调试与泄漏排查；同时会顺手清理已结束的残留项。</p>
     */
    @NonNull
    public static List<String> activeSessionIds() {
        List<String> result = new ArrayList<>();
        for (String id : new ArrayList<>(SESSIONS.keySet())) {
            if (find(id) != null) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * 库内部使用：登记会话。
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static void register(@NonNull MupdfSession session) {
        SESSIONS.put(session.id(), session);
    }

    /**
     * 库内部使用：移除会话。
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static void unregister(@Nullable String sessionId) {
        if (sessionId == null) {
            return;
        }
        SESSIONS.remove(sessionId);
    }

    /**
     * 库内部使用：批量移除（宿主进程侧清理时用）。
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static void unregisterAll(@NonNull Collection<String> sessionIds) {
        for (String id : sessionIds) {
            SESSIONS.remove(id);
        }
    }
}
