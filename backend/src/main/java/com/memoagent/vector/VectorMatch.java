package com.memoagent.vector;

/**
 * 一次向量检索的命中结果。
 * content 是写入时留存的原文，用于排查「为什么召回了这条」，不需要再回 MySQL 取。
 */
public record VectorMatch(String sourceType, long sourceId, String content, double score) {
}
