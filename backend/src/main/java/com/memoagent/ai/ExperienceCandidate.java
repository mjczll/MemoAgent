package com.memoagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperienceCandidate {

    private String title;

    private String problem;

    private String cause;

    private String solution;

    private String lesson;

    private String domain;

    private List<String> tags = new ArrayList<>();

    private KnowledgeCandidate knowledge;
}
