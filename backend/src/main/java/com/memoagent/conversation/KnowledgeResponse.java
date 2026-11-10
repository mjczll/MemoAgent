package com.memoagent.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.memoagent.ai.KnowledgeCandidate;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 整理阶段的知识候选判定结果，顶层只包一层数组。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeResponse {

    private List<KnowledgeCandidate> knowledgeCandidates = new ArrayList<>();
}
