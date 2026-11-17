package com.memoagent.vo;

import lombok.Data;

/** 写入 Vault 的结果。written 为 0 表示内容与磁盘完全一致，什么都没做。 */
@Data
public class ObsidianWriteResultVO {

    /** 实际写入的 Vault 绝对路径 */
    private String vaultPath;

    /** 笔记根目录名，固定为 MemoAgent */
    private String folder;

    private int written;

    private int skipped;

    private int failed;

    private int total;

    /** 有失败文件时的说明（取第一条错误 + 失败数） */
    private String message;
}
