package com.memoagent.conversation;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 流式输出的兜底清洗。
 * <p>
 * 本地模型（gemma4）走的是「把工具描述塞进提示词」的调用方式，不稳定时会出两种脏数据：
 * 一是把 {@code <tool_call>} 这类标记当成正文吐给用户；二是在工具调用前后把同一段回答
 * 重新生成一遍，导致消息里出现两到三段高度重复的内容。
 * <p>
 * 这里只做两件事：剥掉工具标记、发现开头短语再次出现就截断。
 * 这是治标——根因是模型对原生 tool calling 支持不好，换支持该能力的模型才是解法。
 */
public final class StreamTextCleaner {

    /** 覆盖 <tool_call>、</tool_call>、<|tool_call|> 等写法。 */
    private static final Pattern TOOL_TAG =
            Pattern.compile("<\\s*/?\\s*\\|?\\s*tool_call\\s*\\|?\\s*[^>]*>", Pattern.CASE_INSENSITIVE);

    /** 判重取开头这么多字：太短容易误判，太长会漏掉改写过的复述。 */
    private static final int HEAD_LENGTH = 12;

    /** 第二次出现至少要离这么远才算重复，避免正常复用同一个短句。 */
    private static final int MIN_GAP = 40;

    private StreamTextCleaner() {
    }

    /** 整段收尾时调用：剥标记 + 去重复。 */
    public static String clean(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String stripped = TOOL_TAG.matcher(text).replaceAll("").strip();
        return dedupeRepeatedHead(stripped);
    }

    /** 流式阶段逐块调用：只能剥完整落在这一块里的标记，重复检测留给收尾统一做。 */
    public static String cleanChunk(String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return "";
        }
        return TOOL_TAG.matcher(chunk).replaceAll("");
    }

    /**
     * 重复生成的那几段通常以同一句话开头。按开头 N 字找第二次出现的位置，
     * 距离够远就认为后面是复述，截断到第一次结束处。
     */
    private static String dedupeRepeatedHead(String text) {
        if (text.length() < HEAD_LENGTH * 3) {
            return text;
        }
        String head = text.substring(0, HEAD_LENGTH);
        int second = text.indexOf(head, HEAD_LENGTH);
        if (second < MIN_GAP) {
            return text;
        }
        return text.substring(0, second).strip();
    }
}
