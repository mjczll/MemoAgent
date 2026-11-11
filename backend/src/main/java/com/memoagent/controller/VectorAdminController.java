package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.service.VectorReindexService;
import com.memoagent.vo.ReindexResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向量索引的运维入口。阶段 7 才做异步 worker，这里是一次性同步补量。 */
@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
public class VectorAdminController {

    private final VectorReindexService vectorReindexService;

    /** POST /api/vector/reindex —— 存量资产补向量，并清理已删除资产的残留向量。 */
    @PostMapping("/reindex")
    public Result<ReindexResultVO> reindex() {
        return Result.ok(vectorReindexService.reindex());
    }
}
