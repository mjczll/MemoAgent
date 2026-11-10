package com.memoagent.ai;

import com.memoagent.conversation.ConversationPrompts;
import com.memoagent.conversation.DraftSanitizer;
import com.memoagent.exception.BusinessException;
import com.memoagent.service.DiaryService;
import com.memoagent.vo.DiaryDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryAiService {

    private final DiaryGenerationModel model;
    private final DiaryService diaryService;

    public DiaryGeneration generate(String content) {
        String source = requireStory(content);
        DiaryGeneration reply = call(() -> model.generate(source), "这次没有整理成功，原文还在，可以再试一次。");
        return GenerationSanitizer.sanitizeDiary(reply, source);
    }

    public ExperienceExtraction extract(Long diaryId) {
        DiaryDetailVO diary = diaryService.get(diaryId);
        requireStory(text(diary.getTitle()) + "\n" + text(diary.getContent()));
        ExperienceExtraction reply = call(
                () -> model.extract(diary.getTitle(), diary.getContent()),
                "这次没有提炼成功，日记还在，可以再试一次。");
        ExperienceExtraction result = new ExperienceExtraction();
        result.setExperiences(GenerationSanitizer.sanitizeExperiences(
                reply == null ? null : reply.getExperiences(),
                diary.getTitle(),
                text(diary.getTitle()) + "\n" + text(diary.getContent())));
        return result;
    }

    private String requireStory(String content) {
        if (content == null || DraftSanitizer.omitCommands(content).isBlank()) {
            throw BusinessException.badRequest("没有可以整理的内容");
        }
        return content.trim();
    }

    private <T> T call(Supplier<T> action, String failure) {
        if (!model.available()) {
            throw BusinessException.badRequest(ConversationPrompts.unavailableText());
        }
        try {
            return action.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Diary model failed", exception);
            throw BusinessException.badRequest(failure);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
