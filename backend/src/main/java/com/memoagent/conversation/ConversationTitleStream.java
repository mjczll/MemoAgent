package com.memoagent.conversation;

import com.memoagent.entity.Conversation;
import com.memoagent.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 标题生成完成后主动推给前端，替代「前端每 1.5 秒轮询列表」。
 * <p>
 * 一轮对话结束后标题还要再跑一次模型（本地实测 4–12 秒），这段时间前端无事可做：
 * 轮询要么白拉十几次列表，要么停止得太早、界面停在「新对话」。
 * 这里让前端挂一条 SSE，标题一写进库就推 {@code title} 事件并结束连接。
 * <p>
 * 连接有两种收尾：拿到标题（正常）、超时（{@link #TIMEOUT_MS}，前端静默放弃）。
 * 不排队、不重试——标题是锦上添花，丢了下次对话还会再试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTitleStream {

    /** 比标题生成的整体超时（25 秒）多留余量，避免后端还在跑、前端已经放弃。 */
    private static final long TIMEOUT_MS = 40_000L;

    private final ConversationMapper conversationMapper;

    /** 会话 id → 正在等待这条会话标题的连接。 */
    private final Map<Long, List<SseEmitter>> waiters = new ConcurrentHashMap<>();

    /**
     * 订阅某条会话的标题。
     * 若标题已经生成过，立刻回一次并结束——前端不必区分「等」和「已有」。
     */
    public SseEmitter subscribe(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null && StringUtils.hasText(conversation.getTitle())
                && !ConversationTitleService.PLACEHOLDER.equals(conversation.getTitle())) {
            return immediate(conversation.getTitle());
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        waiters.computeIfAbsent(conversationId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(conversationId, emitter));
        emitter.onTimeout(() -> {
            remove(conversationId, emitter);
            emitter.complete();
        });
        emitter.onError(exception -> remove(conversationId, emitter));
        return emitter;
    }

    /** 标题写库成功后调用。没有等待者时什么都不做。 */
    public void publish(Long conversationId, String title) {
        List<SseEmitter> list = waiters.remove(conversationId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("title")
                        .data(Map.of("title", title), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException | IllegalStateException exception) {
                // 前端已经关掉页面或超时，忽略即可
                log.debug("推送标题失败：#{}", conversationId);
            }
        }
    }

    /**
     * 标题生成失败或输出不合格时调用：直接断开等待中的连接，
     * 免得前端为了一个不会来的标题挂到超时。
     */
    public void close(Long conversationId) {
        List<SseEmitter> list = waiters.remove(conversationId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }

    private SseEmitter immediate(String title) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event()
                    .name("title")
                    .data(Map.of("title", title), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void remove(Long conversationId, SseEmitter emitter) {
        List<SseEmitter> list = waiters.get(conversationId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            waiters.remove(conversationId, list);
        }
    }
}
