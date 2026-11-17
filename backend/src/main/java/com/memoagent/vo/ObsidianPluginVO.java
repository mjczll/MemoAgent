package com.memoagent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 插件绑定状态。Web 设置页据此决定显示「去配对」还是「已连接 xxx」。 */
@Data
public class ObsidianPluginVO {

    private boolean paired;

    private String installationId;

    private String vaultName;

    private LocalDateTime pairedAt;

    private LocalDateTime lastActiveAt;
}
