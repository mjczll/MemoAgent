package com.memoagent.vo;

import lombok.Data;

@Data
public class StatsVO {

    private long diaries;
    private long experiences;
    private long knowledge;
    private long mastered;
    private int masteryRate;
    private long domains;
    private long interviewOrigin;
    private long linkCount;
}
