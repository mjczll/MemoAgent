package com.memoagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperienceExtraction {

    private List<ExperienceCandidate> experiences = new ArrayList<>();
}
