package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("experience_knowledge")
public class ExperienceKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long experienceId;

    private Long knowledgeId;
}
