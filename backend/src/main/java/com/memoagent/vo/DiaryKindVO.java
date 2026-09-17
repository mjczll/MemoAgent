package com.memoagent.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DiaryKindVO {

    private Long id;
    private String name;
    private Integer sortOrder;

    @JsonProperty("isDefault")
    private boolean isDefault;

    private long diaryCount;
}
