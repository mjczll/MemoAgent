package com.memoagent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationListItemVO {

    private Long id;

    private String title;

    private String status;

    /** 是否置顶 */
    private Boolean pinned;

    private LocalDateTime updatedAt;
}
