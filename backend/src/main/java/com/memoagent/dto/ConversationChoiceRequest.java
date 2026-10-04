package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationChoiceRequest {

    @NotBlank(message = "请选择写入或跳过")
    private String choice;

    private Long knowledgeId;
}
