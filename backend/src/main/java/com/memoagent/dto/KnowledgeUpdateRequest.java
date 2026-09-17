package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeUpdateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    @Size(max = 64, message = "分类不能超过64字")
    private String category;

    @Size(max = 64, message = "领域不能超过64字")
    private String domain;

    @Size(max = 500, message = "摘要不能超过500字")
    private String summary;

    @NotBlank(message = "正文不能为空")
    private String content;

    private String mastery;

    private List<String> tags;

    private List<Long> relatedIds;

    private List<Long> sourceExperienceIds;
}
