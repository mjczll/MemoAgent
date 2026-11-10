package com.memoagent.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConversationCommitRequest {

    private String title;

    private String date;

    private String summary;

    private String content;

    private String kind;

    private List<String> tags;

    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    private List<Long> knowledgeIds;

    private List<Long> experienceIds;

    /** 用户在草稿页勾选的知识候选，未勾选的不回传。 */
    private List<KnowledgeDraftRequest> knowledgeDrafts;
}
