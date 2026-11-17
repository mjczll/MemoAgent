package com.memoagent.vo;

import lombok.Data;

/** 导出前的统计预览：让用户在点按钮前知道会导出多少篇。 */
@Data
public class ObsidianPreviewVO {

    private int diaries;

    private int experiences;

    private int knowledge;

    /** 三类资产的总篇数，不含 README.md（说明页，界面不显示为"篇"） */
    private int total;
}
