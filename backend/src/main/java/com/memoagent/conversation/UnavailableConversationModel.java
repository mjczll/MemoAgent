package com.memoagent.conversation;

import com.memoagent.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
public class UnavailableConversationModel implements ConversationModel {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public TurnReply reply(String prompt) {
        throw BusinessException.badRequest("模型尚未配置");
    }

    @Override
    public Flux<String> streamReply(String prompt, Object turnTools, Object assetTools) {
        return Flux.just(ConversationPrompts.unavailableText());
    }

    @Override
    public DraftReply draft(String prompt) {
        throw BusinessException.badRequest("模型尚未配置");
    }

    @Override
    public KnowledgeResponse knowledge(String prompt) {
        throw BusinessException.badRequest("模型尚未配置");
    }

    @Override
    public String title(String prompt) {
        // 没有模型就不起标题，调用方按「保持占位符」处理
        return null;
    }
}
