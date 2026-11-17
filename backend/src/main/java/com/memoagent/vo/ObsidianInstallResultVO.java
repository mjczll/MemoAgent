package com.memoagent.vo;

import lombok.Data;

/**
 * 「一键装插件」的结果。
 *
 * installed=false 表示后端这台机器上没有可写的 Vault（典型情况：后端部署在云端），
 * 此时 message 说明原因，前端改走「下载插件包 + 点连接链接」那条路。
 */
@Data
public class ObsidianInstallResultVO {

    private boolean installed;

    /** 之前就装过：这次只是刷新了插件文件与配置 */
    private boolean alreadyInstalled;

    private String vaultPath;

    private String vaultName;

    /** 前端据此先把界面切到「已连接」，不必等下一次状态刷新 */
    private String installationId;

    private String message;
}
