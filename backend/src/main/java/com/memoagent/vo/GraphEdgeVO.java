package com.memoagent.vo;

import lombok.Data;

/** 知识图谱边，字段与前端 GraphEdge 同构。label 为五种关系：提炼 / 沉淀 / 关联 / 相关 / 归属。 */
@Data
public class GraphEdgeVO {

    private String id;

    private String source;

    private String target;

    private String label;
}
