package com.memoagent.vo;

import lombok.Data;

/** 单个文件的处理结果。既用于插件回报，也用于 Web 展示失败明细。 */
@Data
public class ObsidianFileResultVO {

    private String path;

    /** SUCCESS / SKIPPED / FAILED / CONFLICT */
    private String status;

    private String error;
}
