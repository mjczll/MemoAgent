package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ExperienceUpdateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    @NotBlank(message = "问题不能为空")
    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    @Size(max = 64, message = "领域不能超过64字")
    private String domain;

    private List<String> tags;

    private List<Long> knowledgeIds;
}
