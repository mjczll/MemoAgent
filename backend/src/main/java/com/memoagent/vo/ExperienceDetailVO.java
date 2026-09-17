package com.memoagent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExperienceDetailVO {

    private Long id;
    private String title;
    private String problem;
    private String cause;
    private String solution;
    private String lesson;
    private List<String> tags;
    private String domain;
    private Long diaryId;
    private String diaryTitle;
    private List<Long> knowledgeIds;
    private String visibility;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;
}
