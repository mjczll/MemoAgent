package com.memoagent.vo;

import lombok.Data;

/**
 * 写入 Vault 的请求体。vaultPath 为空时用自动识别的当前 Vault；
 * 传了就只允许是 obsidian.json 里登记过的目录（后端会校验，防止被当成任意路径写文件）。
 */
@Data
public class ObsidianWriteRequest {

    private String vaultPath;
}
