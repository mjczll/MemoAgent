package com.memoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoagent.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 待办变更的 SSE 推送（2026-11-17 选型，见开发步骤文档 18.2）。
 * <p>
 * 待办状态只有两种翻转时刻：① 用户动作（整理生成草稿 / 提交 / 删除 / 生成回顾）——
 * 服务层在事务提交后调 {@link #notifyAfterCommit()} 即刻广播；
 * ② 时间到点（过 scheduledAt）——{@link #detectChange()} 每 30 秒比对一次待办签名，
 * 变了才广播。前端收到事件后调一次 {@code GET /api/todos} 拉全量，推送体保持轻。
 * <p>
 * 连接不设超时，靠心跳探活：发送失败即移除，前端 {@code EventSource} 自带断线重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoStreamService {

    private final TodoService todoService;
    private final ObjectMapper objectMapper;

    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    /** 上次广播时的待办签名（type:count 拼接），用于到点检测判重。 */
    private volatile String lastSignature = "";

    /** 前端订阅：EventSource 直连，连接归 Spring MVC 管理，这里只登记与清理。 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(exception -> subscribers.remove(emitter));
        log.debug("待办 SSE 订阅：当前 {} 条连接", subscribers.size());
        return emitter;
    }

    /** 用户动作翻转点：事务提交后再广播，避免前端拉全量时读到未提交的旧状态。 */
    public void notifyAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyChanged();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyChanged();
            }
        });
    }

    /** 立刻广播 todos-changed。 */
    public void notifyChanged() {
        lastSignature = signature();
        broadcast();
    }

    /** 到点检测：签名（type:count 拼接）与上次不同才广播。查询失败不动签名，避免误推。 */
    @Scheduled(fixedDelay = 30_000)
    public void detectChange() {
        String signature = signature();
        if (!signature.equals(lastSignature)) {
            log.info("待办状态变化（签名 {} → {}），广播 todos-changed", lastSignature, signature);
            lastSignature = signature;
            broadcast();
        }
    }

    /** 心跳探活：注释帧不触发前端 message 事件，连接死掉时发送抛异常即清理。 */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception exception) {
                subscribers.remove(emitter);
                emitter.complete();
            }
        }
    }

    private void broadcast() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().data("todos-changed"));
            } catch (Exception exception) {
                subscribers.remove(emitter);
                emitter.complete();
            }
        }
    }

    private String signature() {
        try {
            List<TodoVO> todos = todoService.getTodos();
            return todos.stream()
                    .map(todo -> todo.getType() + ":" + todo.getCount())
                    .collect(Collectors.joining(","));
        } catch (Exception exception) {
            log.warn("待办签名计算失败，保持上次签名", exception);
            return lastSignature;
        }
    }
}
