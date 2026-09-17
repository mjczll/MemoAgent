package com.memoagent.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DiaryKindReorderRequest {

    @NotEmpty(message = "排序列表不能为空")
    private List<Long> ids;
}
