package com.memoagent.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class SpringAiConversationModel implements ConversationModel {

    private final ChatClient chatClient;

    /** 无状态的只读查询工具，每轮都挂上，让模型能自己决定要不要翻库。 */
    private final AssetQueryTools assetQueryTools;

    /**
     * 启动时把实际生效的模型和地址打到日志，切换本地/云端后可以直接从日志确认连的是哪个。
     */
    public SpringAiConversationModel(ChatClient.Builder builder,
                                     AssetQueryTools assetQueryTools,
                                     @Value("${spring.ai.openai.base-url:未配置}") String baseUrl,
                                     @Value("${spring.ai.openai.chat.options.model:未配置}") String model) {
        this.chatClient = builder.build();
        this.assetQueryTools = assetQueryTools;
        log.info("模型已就绪：{} @ {}（{}）", model, baseUrl, isLocal(baseUrl) ? "本地" : "远程");
    }

    private static boolean isLocal(String baseUrl) {
        return baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") || baseUrl.contains("0.0.0.0");
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
                // 本轮的业务工具（TurnTools，带命中候选与 payload 状态）+ 常驻的只读查询工具
                .tools(tools, assetQueryTools)
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

    @Override
    public KnowledgeResponse knowledge(String prompt) {
        return chatClient.prompt()
                .system(ConversationPrompts.KNOWLEDGE)
                .user(prompt)
                .call()
                .entity(KnowledgeResponse.class);
    }
}
