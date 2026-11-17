package com.memoagent.vo;

import lombok.Data;

/** 配对码：Web 生成后展示给用户，由插件在设置里输入。10 分钟内有效、只能消费一次。 */
@Data
public class ObsidianPairCodeVO {

    private String code;

    private int expiresInSeconds;
}
