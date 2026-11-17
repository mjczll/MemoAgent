package com.memoagent.vo;

import lombok.Data;

/**
 * 配对成功的结果。pluginToken 明文只在这一刻返回一次，服务端只留哈希，
 * 之后再也取不回来——插件必须自己保存好，丢了只能重新配对。
 */
@Data
public class ObsidianPairResultVO {

    private String pluginToken;

    private String installationId;

    private String vaultName;
}
