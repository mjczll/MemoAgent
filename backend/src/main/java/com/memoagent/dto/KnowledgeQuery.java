package com.memoagent.dto;

import lombok.Data;

@Data
public class KnowledgeQuery {

    private String keyword;

    private String domain;

    private String mastery;

    private long page = 1;

    private long size = 100;
}
