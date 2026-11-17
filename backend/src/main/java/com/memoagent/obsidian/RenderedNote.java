package com.memoagent.obsidian;

/**
 * 渲染结果：一篇笔记的最终落盘形态。
 *
 * assetType / assetId 只用于任务追踪（将来做删除同步、双向同步时要靠它对应回资产），
 * 不参与 Markdown 生成，也不出现在笔记正文里。
 */
public record RenderedNote(String path, String content, String hash, String assetType, Long assetId) {
}
