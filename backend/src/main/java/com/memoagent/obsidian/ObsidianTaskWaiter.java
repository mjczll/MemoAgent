package com.memoagent.obsidian;

import com.memoagent.common.Result;
import com.memoagent.vo.ObsidianPluginTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件长轮询的挂起点。
 *
 * 插件发一个 GET 挂在这里等，网页一建任务就把它放行——插件立刻重发，
 * 那一次查询落在新事务里，能看到刚提交的任务。所以「点了按钮到开始写盘」的延迟
 * 从「最多 8 秒」降到一次本地往返。
 *
 * 为什么放行时不直接带任务：建任务的事务此刻可能还没提交，这里查库查不到。
 * 让它多走一次请求（几毫秒）比在 afterCommit 里做回调简单可靠得多。
 *
 * 超时与放行走同一个出口（都返回 data=null），插件端只有一条规则：
 * 没拿到任务就立刻再发。心跳、重连、任务到达因此共用一套逻辑。
 */
@Slf4j
@Component
public class ObsidianTaskWaiter {

    private final Set<Pending> waiters = ConcurrentHashMap.newKeySet();

    /**
     * 挂起一个请求。
     *
     * @param userId        只放行同一个用户的等待者：别人的任务不能被这边领走
     * @param timeoutMillis 超过这个时间返回 data=null，插件会立即重发
     */
    public DeferredResult<Result<ObsidianPluginTaskVO>> await(Long userId, long timeoutMillis) {
        // 必须显式给超时结果：DeferredResult 默认超时会回 503，
        // 那在插件看来就是「服务器出错」，会进退避重试。而等满没任务其实是正常状态。
        DeferredResult<Result<ObsidianPluginTaskVO>> deferred =
                new DeferredResult<>(timeoutMillis, Result.ok(null));
        Pending pending = new Pending(userId, deferred);
        waiters.add(pending);
        deferred.onCompletion(() -> waiters.remove(pending));
        deferred.onTimeout(() -> waiters.remove(pending));
        return deferred;
    }

    /** 某个用户建了新任务：放行他所有在等的插件实例（可能同时在多台机器上开着） */
    public void signal(Long userId) {
        int released = 0;
        for (Pending pending : waiters) {
            if (!pending.userId().equals(userId)) {
                continue;
            }
            // 已超时的就别碰了，onTimeout 会把它从集合里摘掉
            if (!pending.deferred().isSetOrExpired()) {
                // 用「成功的空响应」而不是裸 null：插件解析统一走 {code,message,data}
                pending.deferred().setResult(Result.ok(null));
                released++;
            }
        }
        if (released > 0) {
            log.debug("放行 {} 个 Obsidian 长轮询（userId={}）", released, userId);
        }
    }

    private record Pending(Long userId, DeferredResult<Result<ObsidianPluginTaskVO>> deferred) {
    }
}
