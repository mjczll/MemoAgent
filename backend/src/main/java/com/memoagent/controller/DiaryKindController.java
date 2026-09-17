package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.DiaryKindService;
import com.memoagent.vo.DiaryKindVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
