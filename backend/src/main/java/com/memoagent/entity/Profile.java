package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 个人偏好（阶段 8.2）。单用户，user_id 即主键。 */
@Data
@TableName("profile")
public class Profile {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String displayName;

    private String signature;

    private Integer goalWeekly;

    private String reminderTime;

    /** 每周回顾触发日：0=周日 1=周一 ... 6=周六 */
    private Integer reviewDay;

    /** 每周回顾触发时间 HH:mm */
    private String reviewTime;

    private Boolean autoExtract;

    private Boolean autoSuggestKnowledge;

    private Boolean weeklyReview;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
