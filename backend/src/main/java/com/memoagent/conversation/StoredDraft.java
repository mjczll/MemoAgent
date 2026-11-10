package com.memoagent.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredDraft {

    private boolean incident;

    private String title;

    private String date;

    private String kind;

    private String domain;

    private List<String> tags = new ArrayList<>();

    private String summary;

    private String content;

    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    /** 整理时判出的知识候选，用户在草稿页勾选后才写入知识库。 */
    private List<StoredKnowledgeCandidate> knowledgeCandidates = new ArrayList<>();
}
