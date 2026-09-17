package com.memoagent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeListItemVO {

    private Long id;
    private String title;
    private String category;
    private String domain;
    private List<String> tags;
    private String summary;
    private List<Long> relatedIds;
    private List<Long> sourceExperienceIds;
    private String mastery;
    private String visibility;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;
}
