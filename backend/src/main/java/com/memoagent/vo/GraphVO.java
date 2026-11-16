package com.memoagent.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 知识图谱响应，字段与前端 GraphData 同构。 */
@Data
public class GraphVO {

    private List<GraphNodeVO> nodes = new ArrayList<>();

    private List<GraphEdgeVO> edges = new ArrayList<>();
}
