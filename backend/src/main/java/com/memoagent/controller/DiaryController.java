package com.memoagent.controller;

import com.memoagent.ai.DiaryAiService;
import com.memoagent.ai.DiaryGeneration;
import com.memoagent.ai.ExperienceExtraction;
import com.memoagent.common.Result;
import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.DiaryGenerateRequest;
import com.memoagent.dto.DiaryQuery;
import com.memoagent.dto.DiaryUpdateRequest;
import com.memoagent.service.DiaryService;
import com.memoagent.service.SemanticSearchService;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.DiaryListItemVO;
import com.memoagent.vo.PageData;
import com.memoagent.vo.SearchResultVO;
import com.memoagent.vo.TagCountVO;
import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final DiaryAiService diaryAiService;
    private final SemanticSearchService semanticSearchService;

    @PostMapping
    public Result<DiaryDetailVO> create(@Valid @RequestBody DiaryCreateRequest request) {
        return Result.ok(diaryService.create(request));
    }

    @PostMapping("/generate")
    public Result<DiaryGeneration> generate(@Valid @RequestBody DiaryGenerateRequest request) {
        return Result.ok(diaryAiService.generate(request.getContent()));
    }

    @GetMapping
    public Result<PageData<DiaryListItemVO>> list(DiaryQuery query) {
        return Result.ok(diaryService.list(query));
    }

    /** 标签及各自篇数，供日记页筛选条渲染。 */
    @GetMapping("/tags")
    public Result<List<TagCountVO>> tags() {
        return Result.ok(diaryService.tagSummary());
    }

    @GetMapping("/{id}")
    public Result<DiaryDetailVO> get(@PathVariable Long id) {
        return Result.ok(diaryService.get(id));
    }

    @PostMapping("/{id}/extract")
    public Result<ExperienceExtraction> extract(@PathVariable Long id) {
        return Result.ok(diaryAiService.extract(id));
    }

    /** 相似提醒：返回和这篇日记相关的经验 / 日记 / 知识，已排除它自己。 */
    @GetMapping("/{id}/similar")
    public Result<SearchResultVO> similar(@PathVariable Long id,
                                          @RequestParam(required = false) Integer topK) {
        return Result.ok(semanticSearchService.similarToDiary(id, topK));
    }

    @PutMapping("/{id}")
    public Result<DiaryDetailVO> update(@PathVariable Long id, @Valid @RequestBody DiaryUpdateRequest request) {
        return Result.ok(diaryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id);
        return Result.ok(null);
    }
}
