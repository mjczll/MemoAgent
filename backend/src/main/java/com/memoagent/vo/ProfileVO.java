package com.memoagent.vo;

import lombok.Data;

/** 个人偏好，字段与前端 ProfileSettings 同构。 */
@Data
public class ProfileVO {

    private String displayName;

    private String signature;

    /** 每周回顾触发日：0=周日 1=周一 ... 6=周六 */
    private Integer reviewDay;

    /** 每周回顾触发时间 HH:mm */
    private String reviewTime;

    private Boolean autoExtract;

    private Boolean weeklyReview;
}
