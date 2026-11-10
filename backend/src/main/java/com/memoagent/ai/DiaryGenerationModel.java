package com.memoagent.ai;

public interface DiaryGenerationModel {

    boolean available();

    DiaryGeneration generate(String source);

    ExperienceExtraction extract(String title, String content);
}
