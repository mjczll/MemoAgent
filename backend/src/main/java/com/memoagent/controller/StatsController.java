package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.StatsService;
import com.memoagent.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public Result<StatsVO> get() {
        return Result.ok(statsService.get());
    }
}
