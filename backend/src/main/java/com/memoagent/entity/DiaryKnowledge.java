package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("diary_knowledge")
public class DiaryKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diaryId;

    private Long knowledgeId;
}
