package com.memoagent.dto;

import lombok.Data;

/**
 * 进度上报。插件每处理 10 篇左右报一次，用聚合数字即可；
 * 逐篇明细留到 complete 时一次性提交，避免 200 次细碎请求。
 */
@Data
public class ObsidianProgressRequest {

    private int processed;

    private int success;

    private int skipped;

    private int failed;

    private int conflict;

    private String message;
}
