package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步快照：每个插件实例 × 每个 path 一行。
 *
 * 它是 DELETE 能生成、冲突能判定的唯一依据——没有这张表就只知道「现在有什么」，
 * 不知道「上次同步了什么」，删掉的资产永远无法从 Vault 里清走。
 */
@Data
@TableName("obsidian_sync_state")
public class ObsidianSyncState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String installationId;

    private String path;

    /** path 的 SHA-256，作为唯一键（path 过长无法直接建索引） */
    private String pathHash;

    private String assetType;

    private Long assetId;

    private String lastSyncedHash;

    private LocalDateTime lastSyncedAt;
}
