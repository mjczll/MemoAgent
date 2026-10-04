package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationMessageRequest {

    @NotBlank(message = "内容不能为空")
    private String text;
}
