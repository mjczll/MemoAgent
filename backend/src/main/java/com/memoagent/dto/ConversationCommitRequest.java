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
}
