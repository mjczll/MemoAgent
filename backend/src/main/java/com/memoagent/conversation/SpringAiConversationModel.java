package com.memoagent.conversation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class SpringAiConversationModel implements ConversationModel {

    private final ChatClient chatClient;

    public SpringAiConversationModel(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public TurnReply reply(String prompt) {
        return chatClient.prompt()
                .system(ConversationPrompts.TURN)
                .user(prompt)
                .call()
                .entity(TurnReply.class);
    }

    @Override
    public Flux<String> streamReply(String prompt, Object tools) {
        return chatClient.prompt()
                .system(ConversationPrompts.TURN_STREAM)
                .user(prompt)
                .tools(tools)
                .stream()
                .content();
    }

    @Override
    public DraftReply draft(String prompt) {
        return chatClient.prompt()
                .system(ConversationPrompts.DRAFT)
                .user(prompt)
                .call()
                .entity(DraftReply.class);
    }
}
