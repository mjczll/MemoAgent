package com.memoagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeCandidate {

    private String title;

    private String domain;

    private String category;

    private String summary;

    private String content;

    private List<String> tags = new ArrayList<>();

    /** 用户原话中支撑这条知识的那一句，服务端会做子串校验。 */
    private String evidence;
}
