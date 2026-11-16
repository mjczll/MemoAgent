package com.memoagent.vo;

import lombok.Data;

/** 知识图谱节点，字段与前端 GraphNode 同构。 */
@Data
public class GraphNodeVO {

    /** 实体节点为实体 id 字符串；领域节点为「domain:名称」 */
    private String id;

    private String label;

    /** diary / experience / knowledge / domain */
    private String type;

    private String domain;

    /** 关联的实体 id（领域节点为自身名称） */
    private String refId;

    private String visibility;

    /** 节点大小权重，公式与前端旧版一致 */
    private double weight;

    private String updatedAt;
}
