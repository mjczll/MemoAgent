package com.memoagent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DiaryDetailVO {

    private Long id;
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String kind;
    private Long kindId;
    private String origin;
    private String summary;
    private String content;
    private List<String> tags;
    private String visibility;
    private int experienceCount;
    private int knowledgeCount;
    private List<Long> experienceIds;
    private List<Long> knowledgeIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;
}
