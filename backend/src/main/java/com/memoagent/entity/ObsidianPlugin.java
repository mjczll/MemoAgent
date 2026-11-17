package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Obsidian 插件安装实例。一个 Obsidian 安装 = 一行，配对后长期有效。
 *
 * 只存 token 的哈希：token 明文仅在配对成功那一次响应里返回，之后服务端无从还原。
 */
@Data
@TableName("obsidian_plugin")
public class ObsidianPlugin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 插件生成的安装标识，卸载重装会变 */
    private String installationId;

    /** 绑定的 Vault 名称（目录名） */
    private String vaultName;

    private String tokenHash;

    /** token 首尾各 4 位，仅用于排查是哪个凭证，不足以还原 token */
    private String tokenHint;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;

    /** 1=已撤销 */
    private Integer revoked;
}
