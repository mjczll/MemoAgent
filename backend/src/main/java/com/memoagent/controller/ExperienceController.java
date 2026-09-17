package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.ExperienceQuery;
import com.memoagent.dto.ExperienceUpdateRequest;
import com.memoagent.service.ExperienceService;
import com.memoagent.vo.ExperienceDetailVO;
import com.memoagent.vo.ExperienceListItemVO;
import com.memoagent.vo.PageData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    @PostMapping
    public Result<ExperienceDetailVO> create(@Valid @RequestBody ExperienceCreateRequest request) {
        return Result.ok(experienceService.create(request));
    }

    @GetMapping
    public Result<PageData<ExperienceListItemVO>> list(ExperienceQuery query) {
        return Result.ok(experienceService.list(query));
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
