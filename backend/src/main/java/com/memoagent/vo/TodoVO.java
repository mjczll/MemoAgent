package com.memoagent.vo;

import lombok.Data;

/** 待办通知项（动态计算，不落库）。 */
@Data
public class TodoVO {

    /** weekly-review：本周回顾待生成；drafts：草稿待确认 */
    private String type;

    private String title;

    private String description;

    /** 周回顾待办的计划触发时间（ISO），仅 weekly-review 有值 */
    private String scheduledAt;

    /** 关联数量：weekly-review = 本周新增日记数；drafts = 待确认草稿数 */
    private Long count;
}
