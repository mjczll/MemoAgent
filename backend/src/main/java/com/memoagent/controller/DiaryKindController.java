package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.DiaryKindCreateRequest;
import com.memoagent.dto.DiaryKindReorderRequest;
import com.memoagent.dto.DiaryKindUpdateRequest;
import com.memoagent.service.DiaryKindService;
import com.memoagent.vo.DiaryKindVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/diary-kinds")
@RequiredArgsConstructor
public class DiaryKindController {

    private final DiaryKindService diaryKindService;

    @GetMapping
    public Result<List<DiaryKindVO>> list() {
        return Result.ok(diaryKindService.list());
    }

    @PostMapping
    public Result<DiaryKindVO> create(@Valid @RequestBody DiaryKindCreateRequest request) {
        return Result.ok(diaryKindService.create(request));
    }

    @PutMapping("/reorder")
    public Result<List<DiaryKindVO>> reorder(@Valid @RequestBody DiaryKindReorderRequest request) {
        return Result.ok(diaryKindService.reorder(request));
    }

    @PutMapping("/{id}")
    public Result<DiaryKindVO> update(@PathVariable Long id, @Valid @RequestBody DiaryKindUpdateRequest request) {
        return Result.ok(diaryKindService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryKindService.delete(id);
        return Result.ok(null);
    }
}
