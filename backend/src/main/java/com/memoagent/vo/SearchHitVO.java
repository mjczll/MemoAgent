package com.memoagent.vo;

import lombok.Data;

/** 一条语义检索命中。kind / id 回指 MySQL 资产，正文不复制到向量库。 */
@Data
public class SearchHitVO {

    /** diary / experience / knowledge */
    private String kind;

    private Long id;

    private String title;

    /** 回查 MySQL 得到的正文摘要，用于模型理解与引用展示。 */
    private String excerpt;

    /** 一句话概括。经验取结论，日记/知识取摘要，引用卡片优先用它。 */
    private String summary;

    /** 余弦相似度，越大越相近。 */
    private double score;
}
