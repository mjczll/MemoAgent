package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("experience_tag")
public class ExperienceTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long experienceId;

    private Long tagId;
}
