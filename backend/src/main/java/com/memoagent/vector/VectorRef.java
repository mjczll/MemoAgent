package com.memoagent.vector;

/** 向量表里一条资产的定位信息，用于补量时比对哪些向量已经没有对应 MySQL 记录。 */
public record VectorRef(String sourceType, long sourceId) {
}
