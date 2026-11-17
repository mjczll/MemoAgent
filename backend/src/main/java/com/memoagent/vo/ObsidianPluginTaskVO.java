package com.memoagent.vo;

import lombok.Data;

import java.util.List;

/** 插件领取到的任务。data 为 null 表示当前没有待处理任务。 */
@Data
public class ObsidianPluginTaskVO {

    private String taskId;

    private int total;

    private List<ObsidianTaskFileVO> files;
}
