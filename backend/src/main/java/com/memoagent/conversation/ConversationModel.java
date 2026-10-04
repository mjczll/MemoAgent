package com.memoagent.conversation;

import reactor.core.publisher.Flux;

public interface ConversationModel {

    boolean available();

    TurnReply reply(String prompt);

    Flux<String> streamReply(String prompt, Object tools);

    DraftReply draft(String prompt);
}
