package com.memoagent.vo;

import lombok.Data;

/** 一条语义检索命中。kind / id 回指 MySQL 资产，正文不复制到向量库。 */
@Data
public class SearchHitVO {

    /** diary / experience / knowledge */
    private String kind;

    private Long id;

    private String title;

    /** 回查 MySQL 得到的简短摘要，用于展示和作为引用来源。 */
    private String excerpt;

    /** 余弦相似度，越大越相近。 */
    private double score;
}
