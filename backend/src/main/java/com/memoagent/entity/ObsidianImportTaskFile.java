package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务内的一篇笔记。
 *
 * content 是任务生成那一刻的快照：任务执行期间资产再被编辑，也不会写到不一致的正文。
 * lastSyncedHash 是任务生成时本地已知的同步版本，插件用它区分「本地没改」和「本地也改了」。
 */
@Data
@TableName("obsidian_import_task_file")
public class ObsidianImportTaskFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    /** 相对 Vault 根，固定以 MemoAgent/ 开头 */
    private String path;

    /** DELETE 类为 null */
    private String content;

    /** 内容 SHA-256 前 16 位 */
    private String hash;

    /** CREATE / UPDATE / DELETE */
    private String opType;

    private String assetType;

    private Long assetId;

    private String lastSyncedHash;

    /** PENDING / SUCCESS / SKIPPED / FAILED / CONFLICT */
    private String status;

    private String error;

    private LocalDateTime processedAt;
}
