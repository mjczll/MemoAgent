package com.memoagent.vo;

import com.memoagent.conversation.MessagePayload;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageVO {

    private Long id;

    private String role;

    private String kind;

    private String text;

    private MessagePayload payload;

    private LocalDateTime createdAt;
}
