package com.memoagent.dto;

import lombok.Data;

/** 个人偏好更新请求：设置页「保存偏好」全量提交。 */
@Data
public class ProfileUpdateRequest {

    private String displayName;

    private String signature;

    private Integer goalWeekly;

    private String reminderTime;

    private Integer reviewDay;

    private String reviewTime;

    private Boolean autoExtract;

    private Boolean autoSuggestKnowledge;

    private Boolean weeklyReview;
}
