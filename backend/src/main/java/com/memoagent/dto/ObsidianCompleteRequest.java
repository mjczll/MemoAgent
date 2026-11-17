package com.memoagent.dto;

import com.memoagent.vo.ObsidianFileResultVO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 任务完成上报。
 *
 * files 是逐篇结果：服务端据此更新同步快照（成功写入的记 hash，删除成功的清掉记录）。
 * 没有 files 时退化为「全部成功且无冲突才更新快照」的保守策略——宁可下次多写一遍，
 * 也不要把失败的文件误记为已同步。
 */
@Data
public class ObsidianCompleteRequest {

    /** COMPLETED / PARTIAL_FAILED / FAILED */
    @NotBlank(message = "status 不能为空")
    private String status;

    private int successCount;

    private int skippedCount;

    private int failedCount;

    private int conflictCount;

    private String message;

    private List<ObsidianFileResultVO> files;
}
