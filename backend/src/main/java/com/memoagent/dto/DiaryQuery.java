package com.memoagent.dto;

import lombok.Data;

@Data
public class DiaryQuery {

    private String keyword;

    private String kind;

    private String sort = "desc";

    private long page = 1;

    private long size = 20;
}
