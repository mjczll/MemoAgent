package com.memoagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 导入任务的进度推送（SSE，与待办的 TodoStreamService 同一套做法）。
 *
 * 与待办不同，这里的推送体直接是任务状态快照而不是"变了"的信号：
 * 进度更新频率低（每 10 篇一次）、结构固定，前端收到就能直接渲染，
 * 省掉"收到信号再拉一次全量"的往返。
 *
 * 连接按 taskId 分组：一次导入只让关这个任务的页面收到事件。
 * 不设超时，靠 15 秒一次的心跳探活，发送失败即摘除；前端 EventSource 自带重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObsidianImportStreamService {

    private final ObjectMapper objectMapper;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * 心跳自己起一条守护线程，不用 @Scheduled：
     * 本项目的 SchedulingConfig 挂在「向量补偿开关」上，关掉向量重试就不注册调度器，
     * 不能因为那个开关让 SSE 连接失去探活。
     */
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "obsidian-import-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void startHeartbeat() {
        heartbeat.scheduleWithFixedDelay(this::ping, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeat.shutdownNow();
    }

    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> {
            remove(taskId, emitter);
            emitter.complete();
        });
        emitter.onError(exception -> remove(taskId, emitter));
        return emitter;
    }

    /** 推一次当前状态。没有订阅者时静默跳过（用户关掉页面了，不需要为此报错）。 */
    public void publish(String taskId, Object payload) {
        List<SseEmitter> targets = subscribers.get(taskId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("导入任务推送序列化失败：{}", taskId, exception);
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (IOException | IllegalStateException exception) {
                remove(taskId, emitter);
            }
        }
    }

    /** 事务提交后再推，避免前端拿到还查不到的中间态 */
    public void publishAfterCommit(String taskId, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(taskId, payload);
                }
            });
            return;
        }
        publish(taskId, payload);
    }

    /** 心跳：只发注释帧，不产生 message 事件，前端 onmessage 不会被触发 */
    private void ping() {
        try {
            subscribers.forEach((taskId, emitters) -> {
                for (SseEmitter emitter : new ArrayList<>(emitters)) {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException exception) {
                        remove(taskId, emitter);
                    }
                }
            });
        } catch (RuntimeException exception) {
            // scheduleWithFixedDelay 遇到未捕获异常会永久停止后续执行，这里必须兜住
            log.warn("导入任务 SSE 心跳异常", exception);
        }
    }

    private void remove(String taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(taskId, emitters);
        }
    }
}
