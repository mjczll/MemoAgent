package com.memoagent.conversation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.Texts;
import com.memoagent.entity.Conversation;
import com.memoagent.entity.ConversationMessage;
import com.memoagent.mapper.ConversationMapper;
import com.memoagent.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 会话标题：用第一轮问答让模型起一个「内容主体」式的短语，替代原来的「首句截断」。
 * <p>
 * 三条硬约束：
 * - 只在标题仍是占位符「新对话」时动手，写库也带同样的条件，用户手动改过就永远不会被覆盖；
 * - 全异步，首轮回复不等它；生成失败保持占位符，下一轮再试（懒重试）；
 * - 模型输出必须过校验，否则丢弃——小模型经常写成问句或直接抄原话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationTitleService {

    /** 会话尚无标题时的占位。前端也按这个值判断要不要轮询刷新。 */
    public static final String PLACEHOLDER = "新对话";

    /** 送进模型的原文长度上限，标题只需要知道「聊的是什么」。 */
    private static final int MAX_INPUT_CHARS = 600;

    /**
     * 最短 2 个字：寒暄类对话模型常给「闲聊」「问候」，
     * 卡到 4 字会把这类对话永远留在占位符上。
     */
    private static final int MIN_TITLE_CHARS = 2;

    private static final int MAX_TITLE_CHARS = 20;

    /** 一眼能看出是「照抄原话」的下限：太短的同名片段不算抄袭（例如「缓存」）。 */
    private static final int COPY_CHECK_CHARS = 6;

    private static final Pattern TRAILING = Pattern.compile("[。．.！!~～,，、；;\\s]+$");

    private static final Pattern PREFIX = Pattern.compile("^(标题|题目|title)\\s*[:：]\\s*", Pattern.CASE_INSENSITIVE);

    private static final String QUOTES = "「」『』“”‘’\"'《》";

    /** 这些词出现在标题开头，说明模型把「谁在说」写进去了，不是内容主体。 */
    private static final List<String> BAD_STARTS = List.of("用户", "请问", "帮我", "这是一段", "这段对话");

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationModel conversationModel;

    /**
     * 首轮结束后调用。不阻塞调用方，异常全部吞掉——标题只是锦上添花，不能影响对话。
     */
    @Async("titleExecutor")
    public void generate(Long conversationId) {
        try {
            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !PLACEHOLDER.equals(conversation.getTitle())) {
                return;
            }
            List<ConversationMessage> messages = messageMapper.selectList(
                    Wrappers.<ConversationMessage>lambdaQuery()
                            .eq(ConversationMessage::getConversationId, conversationId)
                            .orderByAsc(ConversationMessage::getId)
                            .last("limit 6"));
            String question = firstByRole(messages, "user");
            String answer = firstByRole(messages, "assistant");
            if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
                log.info("会话 #{} 还没有一问一答，跳过起标题（user={}，assistant={}）",
                        conversationId, StringUtils.hasText(question), StringUtils.hasText(answer));
                return;
            }
            log.info("会话 #{} 开始生成标题，问题长度 {}", conversationId, question.length());
            String raw = conversationModel.title(prompt(question, answer));
            String title = sanitize(raw, question);
            if (title == null) {
                // 不打 debug：标题没生成时最需要知道原因，而这段一轮只跑一次
                log.info("会话 #{} 的标题未通过校验，保持「{}」：{}", conversationId, PLACEHOLDER, raw);
                return;
            }
            int updated = conversationMapper.update(null, Wrappers.<Conversation>lambdaUpdate()
                    .eq(Conversation::getId, conversationId)
                    .eq(Conversation::getTitle, PLACEHOLDER)
                    .set(Conversation::getTitle, title));
            if (updated > 0) {
                log.info("会话标题已生成：#{} → {}", conversationId, title);
            }
        } catch (Exception exception) {
            log.warn("会话标题生成失败，保持占位符：#{}", conversationId, exception);
        }
    }

    private String prompt(String question, String answer) {
        return "用户说：\n" + Texts.truncate(question, MAX_INPUT_CHARS)
                + "\n\n助手回复：\n" + Texts.truncate(answer, MAX_INPUT_CHARS);
    }

    private String firstByRole(List<ConversationMessage> messages, String role) {
        return messages.stream()
                .filter(message -> role.equals(message.getRole()))
                .map(ConversationMessage::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验并规整模型输出，不合格返回 null（调用方保持占位符）。
     * 规则都在这里，宁可留「新对话」，也不要写进去一个问句或半截原话。
     */
    String sanitize(String raw, String question) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.strip();
        // 只取第一行，模型偶尔会带上解释
        int lineBreak = text.indexOf('\n');
        if (lineBreak > 0) {
            text = text.substring(0, lineBreak).strip();
        }
        text = PREFIX.matcher(text).replaceFirst("");
        text = stripQuotes(text);
        text = TRAILING.matcher(text).replaceAll("");
        text = stripQuotes(text).strip();
        if (text.isEmpty()) {
            return null;
        }
        // 问句一律不要：标题应该是「聊了什么」，不是「问了什么」
        if (text.contains("？") || text.contains("?")) {
            return null;
        }
        int length = text.codePointCount(0, text.length());
        if (length < MIN_TITLE_CHARS || length > MAX_TITLE_CHARS) {
            return null;
        }
        for (String bad : BAD_STARTS) {
            if (text.startsWith(bad)) {
                return null;
            }
        }
        // 和用户原话高度重合＝没概括，只是抄了一句；
        // 短标题（如「你好」）用整句相等判断，避免把合理的两字短语也误杀
        if (text.equals(question.strip())) {
            return null;
        }
        if (length >= COPY_CHECK_CHARS && question.contains(text)) {
            return null;
        }
        return text;
    }

    private String stripQuotes(String text) {
        String value = text.strip();
        while (!value.isEmpty() && QUOTES.indexOf(value.charAt(0)) >= 0) {
            value = value.substring(1).strip();
        }
        while (!value.isEmpty() && QUOTES.indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).strip();
        }
        return value;
    }
}
