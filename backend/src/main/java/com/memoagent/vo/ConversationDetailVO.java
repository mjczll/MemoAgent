package com.memoagent.vo;

import com.memoagent.conversation.RecallHit;
import com.memoagent.conversation.StoredDraft;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationDetailVO {

    private Long id;

    private String title;

    private String status;

    private StoredDraft draft;

    private Long committedDiaryId;

    private LocalDateTime updatedAt;

    private List<ConversationMessageVO> messages = new ArrayList<>();

    private List<RecallHit> related = new ArrayList<>();
}
