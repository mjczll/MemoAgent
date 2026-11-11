package com.memoagent.vector;

/**
 * 资产落库事件。只带定位信息，不带正文。
 * <p>
 * 监听在 MySQL 事务提交之后才回查正文，好处有两个：
 * 1. 拿到的一定是最终落库的内容，而不是内存里还没提交的对象；
 * 2. 事务若回滚，事件根本不会触发，不会留下指向不存在资产的悬空向量。
 * <p>
 * 事件由各资产 Service 发布，一处保存对应一条事件；采访整理走的是同一批 Service，
 * 不需要在对话链路里重复发布。
 */
public record AssetVectorEvent(String sourceType, long sourceId, Operation operation) {

    public enum Operation {
        UPSERT,
        DELETE
    }

    public static AssetVectorEvent upsert(String sourceType, long sourceId) {
        return new AssetVectorEvent(sourceType, sourceId, Operation.UPSERT);
    }

    public static AssetVectorEvent delete(String sourceType, long sourceId) {
        return new AssetVectorEvent(sourceType, sourceId, Operation.DELETE);
    }
}
