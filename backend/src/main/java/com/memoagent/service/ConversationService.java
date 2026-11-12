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

    /** 重命名对话标题，只改标题不动更新时间，避免它因为改名跳到列表顶部 */
    ConversationListItemVO rename(Long id, String title);

    ConversationListItemVO pin(Long id, boolean pinned);

    ConversationDetailVO send(Long id, ConversationMessageRequest request);

    SseEmitter stream(Long id, String text);

    /** 重新生成最后一条回复：丢掉它之后的消息，用同一条用户消息再跑一轮 */
    SseEmitter regenerate(Long id);

    /** 改用户消息后重发：改写该条文本，丢弃它之后的消息，重新生成回复 */
    SseEmitter editStream(Long id, Long messageId, String text);

    ConversationDetailVO choose(Long id, Long messageId, ConversationChoiceRequest request);

    ConversationDetailVO draft(Long id);

    CommitResult commit(Long id, ConversationCommitRequest request);
}
