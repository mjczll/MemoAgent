package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiaryKindCreateRequest {

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 32, message = "类型名称不能超过32字")
    private String name;

    private Boolean isDefault;
}
