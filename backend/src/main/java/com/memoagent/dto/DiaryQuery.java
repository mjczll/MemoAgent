package com.memoagent.dto;

import lombok.Data;

@Data
public class DiaryQuery {

    private String keyword;

    private String kind;

    /** 来源：manual（用户编写）/ interview（采访生成），all 或空表示不限 */
    private String origin;

    /** 标签名，空或 all 表示不限；按 diary_tag 关联筛选 */
    private String tag;

    private String sort = "desc";

    private long page = 1;

    private long size = 20;
}
