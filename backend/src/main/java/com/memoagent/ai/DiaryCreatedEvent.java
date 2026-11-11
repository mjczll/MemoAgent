package com.memoagent.ai;

/**
 * 日记已保存事件。只带 id，监听端自己回查内容。
 * <p>
 * 和 AssetVectorEvent 分开发，是因为两者语义不同：向量事件覆盖三类资产的增删，
 * 这个只关心「一篇手写日记刚刚落库，可以去做经验提取了」。
 * 采访生成的日记不发这个事件——整理阶段已经产出经验，再提一遍是重复劳动。
 */
public record DiaryCreatedEvent(long diaryId) {
}
