package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 插件配对请求：拿 Web 上显示的 6 位码换长期 token。 */
@Data
public class ObsidianPairRequest {

    @NotBlank(message = "配对码不能为空")
    @Size(max = 16, message = "配对码不合法")
    private String code;

    @NotBlank(message = "installationId 不能为空")
    @Size(max = 64, message = "installationId 过长")
    private String installationId;

    @NotBlank(message = "Vault 名称不能为空")
    @Size(max = 128, message = "Vault 名称过长")
    private String vaultName;
}
