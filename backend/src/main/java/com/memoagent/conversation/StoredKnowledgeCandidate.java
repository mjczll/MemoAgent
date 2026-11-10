package com.memoagent.conversation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 清洗后的知识候选，随草稿一起存进 draft_json，供草稿页勾选。 */
@Data
public class StoredKnowledgeCandidate {

    private String title;

    private String domain;

    private String category;

    private String summary;

    private String content;

    private List<String> tags = new ArrayList<>();

    private String evidence;
}
