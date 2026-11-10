package com.memoagent.ai;

import com.memoagent.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
public class UnavailableDiaryGenerationModel implements DiaryGenerationModel {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public DiaryGeneration generate(String source) {
        throw BusinessException.badRequest("模型尚未配置");
    }

    @Override
    public ExperienceExtraction extract(String title, String content) {
        throw BusinessException.badRequest("模型尚未配置");
    }
}
