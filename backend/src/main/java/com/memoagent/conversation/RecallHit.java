package com.memoagent.conversation;

/**
 * 采访里能用到的旧记录。
 * <p>
 * excerpt 是从 MySQL 回查的正文摘要，给模型看，也用于回顾时复述做法；
 * summary 是一句话概括（经验取结论、日记知识取摘要），引用卡片上优先展示它。
 */
public record RecallHit(String type, Long id, String title, String excerpt, String summary) {
}
