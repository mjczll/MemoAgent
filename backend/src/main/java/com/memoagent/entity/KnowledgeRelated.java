package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("knowledge_related")
public class KnowledgeRelated {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private Long relatedId;
}
