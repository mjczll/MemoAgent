package com.memoagent.dto;

import lombok.Data;

@Data
public class DiaryQuery {

    private String keyword;

    /** 来源：manual（用户编写）/ interview（采访生成），all 或空表示不限 */
    private String origin;

    /** 标签名，逗号分隔多个；空或 all 表示不限。多标签为 AND 语义（同时包含）。 */
    private String tags;

    private String sort = "desc";

    private long page = 1;

    private long size = 20;
}
