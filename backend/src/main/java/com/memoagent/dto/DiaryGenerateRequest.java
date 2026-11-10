package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiaryGenerateRequest {

    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文不能超过20000字")
    private String content;
}
