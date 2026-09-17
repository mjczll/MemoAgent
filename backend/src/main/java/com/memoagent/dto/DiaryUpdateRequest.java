package com.memoagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DiaryUpdateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    @NotBlank(message = "正文不能为空")
    private String content;

    @Size(max = 500, message = "摘要不能超过500字")
    private String summary;

    @NotNull(message = "日期不能为空")
    private LocalDate date;

    @NotBlank(message = "类型不能为空")
    private String kind;

    private String origin;

    private List<String> tags;
}
