package com.memoagent.vo;

import lombok.Data;

/** 导出的一篇 Markdown 笔记：路径相对 Vault 根，内容为最终文本。 */
@Data
public class ObsidianNoteVO {

    /** 相对路径，含根目录与扩展名，例如 MemoAgent/Diary/2026-11-18 标题.md */
    private String path;

    private String content;

    /** 内容 SHA-256 前 16 位：前端直写 Vault 时跳过未变化的文件，避免 Obsidian 反复重索引 */
    private String hash;
}
