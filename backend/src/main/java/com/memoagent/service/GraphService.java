package com.memoagent.service;

import com.memoagent.vo.GraphVO;

/** 知识织网：把日记 → 经验 → 知识 → 领域 连成一张图，规则从 fronted/src/lib/graph.ts 收敛而来。 */
public interface GraphService {

    /**
     * 全局织网。
     *
     * @param domain 传领域名则只返回该领域的子图；null 返回全图
     */
    GraphVO graph(String domain);

    /**
     * 局部关系图：以指定类型的实体为中心，BFS 取 depth 跳邻居。
     *
     * @param type  diary / experience / knowledge（三种实体共用自增主键，必须指明类型）
     * @param id    实体 id
     * @param depth BFS 深度
     */
    GraphVO local(String type, Long id, int depth);
}
