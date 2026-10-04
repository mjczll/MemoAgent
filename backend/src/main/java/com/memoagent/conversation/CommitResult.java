package com.memoagent.conversation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommitResult {

    private Long diaryId;

    private Long experienceId;

    private List<Long> knowledgeIds = new ArrayList<>();
}
