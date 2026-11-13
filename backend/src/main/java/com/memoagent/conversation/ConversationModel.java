package com.memoagent.conversation;

import reactor.core.publisher.Flux;

public interface ConversationModel {

    boolean available();

    TurnReply reply(String prompt);

    Flux<String> streamReply(String prompt, Object turnTools, Object assetTools);

    DraftReply draft(String prompt);

    /** 复盘草稿：素材是助手写好的复盘正文，产出与 draft 同构的 DTO，但只关心 title/summary/content/kind/tags。 */
    DraftReply draftReview(String prompt);

    KnowledgeResponse knowledge(String prompt);

    /** 生成会话标题：只返回一行短语，模型不可用时返回 null。 */
    String title(String prompt);
}
