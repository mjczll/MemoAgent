package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.ExperienceQuery;
import com.memoagent.dto.ExperienceUpdateRequest;
import com.memoagent.service.ExperienceService;
import com.memoagent.service.SemanticSearchService;
import com.memoagent.vector.VectorSourceTypes;
import com.memoagent.vo.ExperienceDetailVO;
import com.memoagent.vo.ExperienceListItemVO;
import com.memoagent.vo.PageData;
import com.memoagent.vo.SearchResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;
    private final SemanticSearchService semanticSearchService;

    @PostMapping
    public Result<ExperienceDetailVO> create(@Valid @RequestBody ExperienceCreateRequest request) {
        return Result.ok(experienceService.create(request));
    }

    @GetMapping
    public Result<PageData<ExperienceListItemVO>> list(ExperienceQuery query) {
        return Result.ok(experienceService.list(query));
    }

    /** GET /api/experiences/search?q= —— 只查经验，对应开发文档的相似问题检索。 */
    @GetMapping("/search")
    public Result<SearchResultVO> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Integer topK) {
        return Result.ok(semanticSearchService.search(q,
                List.of(VectorSourceTypes.EXPERIENCE), topK, null));
    }

    @GetMapping("/{id}")
    public Result<ExperienceDetailVO> get(@PathVariable Long id) {
        return Result.ok(experienceService.get(id));
    }

    @PutMapping("/{id}")
    public Result<ExperienceDetailVO> update(@PathVariable Long id, @Valid @RequestBody ExperienceUpdateRequest request) {
        return Result.ok(experienceService.update(id, request));
    }
}
