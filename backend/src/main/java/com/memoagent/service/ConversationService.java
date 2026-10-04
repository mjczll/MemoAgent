package com.memoagent.service;

import com.memoagent.conversation.CommitResult;
import com.memoagent.dto.ConversationChoiceRequest;
import com.memoagent.dto.ConversationCommitRequest;
import com.memoagent.dto.ConversationCreateRequest;
import com.memoagent.dto.ConversationMessageRequest;
import com.memoagent.vo.ConversationDetailVO;
import com.memoagent.vo.ConversationListItemVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ConversationService {

    ConversationDetailVO create(ConversationCreateRequest request);

    List<ConversationListItemVO> list();

    ConversationDetailVO get(Long id);

    void delete(Long id);

    ConversationDetailVO send(Long id, ConversationMessageRequest request);

    SseEmitter stream(Long id, String text);

    ConversationDetailVO choose(Long id, Long messageId, ConversationChoiceRequest request);

    ConversationDetailVO draft(Long id);

    CommitResult commit(Long id, ConversationCommitRequest request);
}
