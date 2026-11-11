package com.memoagent.controller;

import com.memoagent.ai.DiaryCandidateService;
import com.memoagent.common.Result;
import com.memoagent.vo.DiaryCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自动提取出的经验候选。候选只是提案，采纳与否由用户决定，
 * 这正是阶段 3 定的「模型产出不直接入库」规则。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiaryCandidateController {

    private final DiaryCandidateService diaryCandidateService;

    /** 某篇日记下待确认的候选。已采纳 / 已忽略的不再返回。 */
    @GetMapping("/diaries/{diaryId}/candidates")
    public Result<List<DiaryCandidateVO>> list(@PathVariable Long diaryId) {
        return Result.ok(diaryCandidateService.listPending(diaryId));
    }

    /** 采纳：创建经验并返回经验 id。 */
    @PostMapping("/candidates/{id}/accept")
    public Result<Long> accept(@PathVariable Long id) {
        return Result.ok(diaryCandidateService.accept(id));
    }

    @PostMapping("/candidates/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        diaryCandidateService.ignore(id);
        return Result.ok(null);
    }
}
