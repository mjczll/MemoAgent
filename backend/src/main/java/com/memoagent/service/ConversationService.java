package com.memoagent.service;

import com.memoagent.conversation.CommitResult;
import com.memoagent.conversation.RecallHit;
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

    /**
     * 草稿「相关记录」召回（语义检索，需调一次 embedding）。独立于 get：
     * 召回耗时远高于普通 SQL，拆开后详情首屏不被拖慢，前端异步补齐面板。
     */
    List<RecallHit> related(Long id);

    void delete(Long id);

    /** 重命名对话标题，只改标题不动更新时间，避免它因为改名跳到列表顶部 */
    ConversationListItemVO rename(Long id, String title);

    ConversationListItemVO pin(Long id, boolean pinned);

    /**
     * 生成本周回顾（待办通知触发，非定时自动执行）：取本周记录清单 → 生成复盘草稿 →
     * 存为独立会话（status=drafted），用户在草稿页确认提交。
     *
     * @return 新建的回顾会话 id
     */
    Long generateWeeklyReview();

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
