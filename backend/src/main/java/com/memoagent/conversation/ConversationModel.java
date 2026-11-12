package com.memoagent.conversation;

import reactor.core.publisher.Flux;

public interface ConversationModel {

    boolean available();

    TurnReply reply(String prompt);

    Flux<String> streamReply(String prompt, Object tools);

    DraftReply draft(String prompt);

    KnowledgeResponse knowledge(String prompt);

    /** 生成会话标题：只返回一行短语，模型不可用时返回 null。 */
    String title(String prompt);
}
