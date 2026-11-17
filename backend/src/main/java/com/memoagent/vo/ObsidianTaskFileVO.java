package com.memoagent.vo;

import lombok.Data;

/** 任务内的一篇笔记，下发给插件执行。DELETE 类的 content 为空。 */
@Data
public class ObsidianTaskFileVO {

    private String path;

    private String content;

    /** 内容指纹，插件据此判断本地是否需要写 */
    private String hash;

    /** CREATE / UPDATE / DELETE */
    private String opType;

    private String assetType;

    private Long assetId;

    /** 上次成功同步时的 hash；空串 = 这台机器从未同步过 */
    private String lastSyncedHash;
}
