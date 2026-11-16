package com.memoagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memoagent.common.Result;
import com.memoagent.service.GraphService;
import com.memoagent.vo.GraphVO;

import lombok.RequiredArgsConstructor;

/** 知识图谱：阶段 8.1 织网后端化。 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /** 全局织网；domain 传领域名则只返回该领域子图。 */
    @GetMapping
    public Result<GraphVO> graph(@RequestParam(required = false) String domain) {
        return Result.ok(graphService.graph(domain));
    }

    /** 局部关系图：type 为 diary / experience / knowledge（实体共用主键，必须带类型）。 */
    @GetMapping("/local/{id}")
    public Result<GraphVO> local(@PathVariable Long id,
            @RequestParam String type,
            @RequestParam(defaultValue = "2") Integer depth) {
        int safeDepth = Math.max(1, Math.min(depth, 3));
        return Result.ok(graphService.local(type, id, safeDepth));
    }
}
