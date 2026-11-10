package com.memoagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiaryGeneration {

    private String title;

    private String summary;

    private String content;

    private String kind;

    private List<String> tags = new ArrayList<>();

    private List<ExperienceCandidate> experiences = new ArrayList<>();
}
