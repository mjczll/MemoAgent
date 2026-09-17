package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.KnowledgeCreateRequest;
import com.memoagent.dto.KnowledgeQuery;
import com.memoagent.dto.KnowledgeUpdateRequest;
import com.memoagent.service.KnowledgeService;
import com.memoagent.vo.KnowledgeDetailVO;
import com.memoagent.vo.KnowledgeListItemVO;
import com.memoagent.vo.PageData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public Result<KnowledgeDetailVO> create(@Valid @RequestBody KnowledgeCreateRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @GetMapping
    public Result<PageData<KnowledgeListItemVO>> list(KnowledgeQuery query) {
        return Result.ok(knowledgeService.list(query));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeDetailVO> get(@PathVariable Long id) {
        return Result.ok(knowledgeService.get(id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeDetailVO> update(@PathVariable Long id, @Valid @RequestBody KnowledgeUpdateRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }
}
