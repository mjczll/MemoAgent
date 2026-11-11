package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记自动提取出的经验候选。表结构见 db/diary-candidate.sql。
 * <p>
 * 候选不是经验：只有用户在详情页点「采纳」才会写入 experience 表。
 * tags / knowledge 以 JSON 文本存，因为候选是模型产出的一次性快照，不需要按标签检索。
 */
@Data
@TableName("diary_candidate")
public class DiaryCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long diaryId;

    private String title;

    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    private String domain;

    /** 标签数组的 JSON，例如 ["Redis","缓存击穿"] */
    private String tagsJson;

    /** 随附知识候选的 JSON，可为空 */
    private String knowledgeJson;

    /** pending / accepted / ignored */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
