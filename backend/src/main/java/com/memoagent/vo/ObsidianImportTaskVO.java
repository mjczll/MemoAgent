package com.memoagent.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 导入任务状态。Web 首屏拉一次，之后靠 SSE 推送同结构的更新。 */
@Data
public class ObsidianImportTaskVO {

    private String taskId;

    private String status;

    private int total;

    private int processed;

    private int successCount;

    private int skippedCount;

    private int failedCount;

    private int conflictCount;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime expiredAt;

    /** 仅失败与冲突的文件，成功的不回传，避免 200 条明细占满响应 */
    private List<ObsidianFileResultVO> problems;
}
