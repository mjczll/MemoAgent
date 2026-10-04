package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.conversation.CommitResult;
import com.memoagent.dto.ConversationChoiceRequest;
import com.memoagent.dto.ConversationCommitRequest;
import com.memoagent.dto.ConversationCreateRequest;
import com.memoagent.dto.ConversationMessageRequest;
import com.memoagent.service.ConversationService;
import com.memoagent.vo.ConversationDetailVO;
import com.memoagent.vo.ConversationListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public Result<ConversationDetailVO> create(@RequestBody(required = false) ConversationCreateRequest request) {
        return Result.ok(conversationService.create(request));
    }

    @GetMapping
    public Result<List<ConversationListItemVO>> list() {
        return Result.ok(conversationService.list());
    }

    @GetMapping("/{id}")
    public Result<ConversationDetailVO> get(@PathVariable Long id) {
        return Result.ok(conversationService.get(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/messages")
    public Result<ConversationDetailVO> send(@PathVariable Long id, @Valid @RequestBody ConversationMessageRequest request) {
        return Result.ok(conversationService.send(id, request));
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @Valid @RequestBody ConversationMessageRequest request) {
        return conversationService.stream(id, request.getText().trim());
    }

    @PostMapping("/{id}/messages/{messageId}/choice")
    public Result<ConversationDetailVO> choose(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @Valid @RequestBody ConversationChoiceRequest request) {
        return Result.ok(conversationService.choose(id, messageId, request));
    }

    @PostMapping("/{id}/draft")
    public Result<ConversationDetailVO> draft(@PathVariable Long id) {
        return Result.ok(conversationService.draft(id));
    }

    @PostMapping("/{id}/commit")
    public Result<CommitResult> commit(
            @PathVariable Long id,
            @RequestBody(required = false) ConversationCommitRequest request) {
        return Result.ok(conversationService.commit(id, request));
    }
}
