package com.memoagent.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class SpringAiDiaryGenerationModel implements DiaryGenerationModel {

    private final ChatClient chatClient;
    private final String diaryPrompt;
    private final String experiencePrompt;
    private final String knowledgePrompt;

    public SpringAiDiaryGenerationModel(ChatClient.Builder builder) {
        this.chatClient = builder.build();
        this.diaryPrompt = PromptFiles.load("prompts/diary-generation.st");
        this.experiencePrompt = PromptFiles.load("prompts/experience-extraction.st");
        this.knowledgePrompt = PromptFiles.load("prompts/knowledge-extraction.st");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public DiaryGeneration generate(String source) {
        return chatClient.prompt()
                .system(diaryPrompt + "\n\n" + knowledgePrompt)
                .user(source)
                .call()
                .entity(DiaryGeneration.class);
    }

    @Override
    public ExperienceExtraction extract(String title, String content) {
        String user = """
                标题：
                %s

                正文：
                %s
                """.formatted(title == null ? "" : title, content == null ? "" : content);
        return chatClient.prompt()
                .system(experiencePrompt + "\n\n" + knowledgePrompt)
                .user(user)
                .call()
                .entity(ExperienceExtraction.class);
    }
}
