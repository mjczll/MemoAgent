package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.DiaryQuery;
import com.memoagent.dto.DiaryUpdateRequest;
import com.memoagent.service.DiaryService;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.DiaryListItemVO;
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
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping
    public Result<DiaryDetailVO> create(@Valid @RequestBody DiaryCreateRequest request) {
        return Result.ok(diaryService.create(request));
    }

    @GetMapping
    public Result<PageData<DiaryListItemVO>> list(DiaryQuery query) {
        return Result.ok(diaryService.list(query));
    }

    @GetMapping("/{id}")
    public Result<DiaryDetailVO> get(@PathVariable Long id) {
        return Result.ok(diaryService.get(id));
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
