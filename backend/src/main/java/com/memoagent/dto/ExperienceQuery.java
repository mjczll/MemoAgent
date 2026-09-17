package com.memoagent.dto;

import lombok.Data;

@Data
public class ExperienceQuery {

    private String keyword;

    private String domain;

    private long page = 1;

    private long size = 100;
}
