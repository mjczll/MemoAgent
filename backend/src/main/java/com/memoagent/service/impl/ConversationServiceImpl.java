package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.DiaryKinds;
import com.memoagent.common.DiaryOrigins;
import com.memoagent.common.Texts;
import com.memoagent.conversation.CommitResult;
import com.memoagent.conversation.ConversationModel;
import com.memoagent.conversation.ConversationPrompts;
import com.memoagent.conversation.ConversationRecall;
import com.memoagent.conversation.DraftReply;
import com.memoagent.conversation.DraftSanitizer;
import com.memoagent.conversation.MessagePayload;
import com.memoagent.conversation.OrganizeReady;
import com.memoagent.conversation.RecallHit;
import com.memoagent.conversation.TurnTools;
import com.memoagent.conversation.StoredDraft;
import com.memoagent.conversation.TurnReply;
import com.memoagent.conversation.TurnSanitizer;
import com.memoagent.dto.ConversationChoiceRequest;
import com.memoagent.dto.ConversationCommitRequest;
import com.memoagent.dto.ConversationCreateRequest;
import com.memoagent.dto.ConversationMessageRequest;
import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.KnowledgeCreateRequest;
import com.memoagent.dto.KnowledgeDraftRequest;
import com.memoagent.entity.Conversation;
import com.memoagent.entity.ConversationMessage;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.ConversationMapper;
import com.memoagent.mapper.ConversationMessageMapper;
import com.memoagent.service.ConversationService;
import com.memoagent.service.DiaryService;
import com.memoagent.service.ExperienceService;
import com.memoagent.service.KnowledgeService;
import com.memoagent.vo.ConversationDetailVO;
import com.memoagent.vo.ConversationListItemVO;
import com.memoagent.vo.ConversationMessageVO;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.ExperienceDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String ACTIVE = "active";
    private static final String DRAFTED = "drafted";
    private static final String COMMITTED = "committed";
    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final String ACCEPT = "accept";
    private static final String SKIP = "skip";
    private static final String DEFAULT_TITLE = "新的对话";

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationModel conversationModel;
    private final ConversationRecall conversationRecall;
    private final DiaryService diaryService;
    private final ExperienceService experienceService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ConversationDetailVO create(ConversationCreateRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(CurrentUser.id());
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setStatus(ACTIVE);
        conversationMapper.insert(conversation);
        if (request != null && StringUtils.hasText(request.getOpening())) {
            return sendText(conversation.getId(), request.getOpening().trim());
        }
        return detail(requireOwned(conversation.getId()));
    }

    @Override
    public List<ConversationListItemVO> list() {
        return conversationMapper.selectList(Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getUserId, CurrentUser.id())
                        .orderByDesc(Conversation::getUpdatedAt)
                        .orderByDesc(Conversation::getId)
                        .last("limit 50"))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Override
    public ConversationDetailVO get(Long id) {
        return detail(requireOwned(id));
    }

    @Override
    public void delete(Long id) {
        requireOwned(id);
        conversationMapper.deleteById(id);
    }

    @Override
    @Transactional
    public ConversationDetailVO send(Long id, ConversationMessageRequest request) {
        return sendText(id, request.getText().trim());
    }

    @Override
    public SseEmitter stream(Long id, String text) {
        Conversation conversation = requireOpen(id);
        insertMessage(conversation.getId(), USER, TurnSanitizer.INTERVIEW, text, null);
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.setTitle(Texts.truncate(text, 24));
        }
        if (DRAFTED.equals(conversation.getStatus())) {
            conversation.setStatus(ACTIVE);
        }
        touch(conversation);
        List<String> keywords = ConversationRecall.keywords(text);
        List<RecallHit> hits = conversationRecall.find(text);
        String prompt = turnPrompt(conversation.getId(), text, hits);
        TurnTools tools = new TurnTools(hits);
        SseEmitter emitter = new SseEmitter(180_000L);
        StringBuilder visible = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        Runnable stop = () -> {
            Disposable disposable = subscription.get();
            if (disposable != null) {
                disposable.dispose();
            }
            finishStream(emitter, finished, conversation, tools, keywords, visible, false, true);
        };
        emitter.onTimeout(() -> {
            finishStream(emitter, finished, conversation, tools, keywords, visible, true, false);
            Disposable disposable = subscription.get();
            if (disposable != null) {
                disposable.dispose();
            }
        });
        emitter.onError(error -> stop.run());
        emitter.onCompletion(() -> {
            Disposable disposable = subscription.get();
            if (disposable != null) {
                disposable.dispose();
            }
        });
        subscription.set(conversationModel.streamReply(prompt, tools).subscribe(
                token -> {
                    if (token != null && !token.isEmpty()) {
                        visible.append(token);
                        if (!emitVisible(emitter, token)) {
                            stop.run();
                        }
                    }
                },
                error -> {
                    if (finished.get()) {
                        return;
                    }
                    if (stopped(error)) {
                        stop.run();
                        return;
                    }
                    log.warn("Conversation stream failed", error);
                    finishStream(emitter, finished, conversation, tools, keywords, visible, true, false);
                },
                () -> finishStream(emitter, finished, conversation, tools, keywords, visible, false, false)));
        return emitter;
    }

    @Override
    @Transactional
    public ConversationDetailVO choose(Long id, Long messageId, ConversationChoiceRequest request) {
        Conversation conversation = requireOpen(id);
        ConversationMessage message = requireMessage(conversation.getId(), messageId);
        if (!ASSISTANT.equals(message.getRole())) {
            throw BusinessException.badRequest("只能对助手的消息做选择");
        }
        String choice = normalizeChoice(request.getChoice());
        MessagePayload payload = readPayload(message.getPayloadJson());
        if (payload.getSupplement() != null && request.getKnowledgeId() == null) {
            if (StringUtils.hasText(payload.getChoice())) {
                throw BusinessException.badRequest("这条补充已经选过了");
            }
            payload.setChoice(choice);
        } else if (request.getKnowledgeId() != null) {
            MessagePayload.LinkPayload link = payload.getLinks().stream()
                    .filter(item -> request.getKnowledgeId().equals(item.getKnowledgeId()))
                    .findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("这条知识不在本次候选里"));
            if (StringUtils.hasText(link.getChoice())) {
                throw BusinessException.badRequest("这条知识已经选过了");
            }
            link.setChoice(choice);
        } else {
            throw BusinessException.badRequest("这条消息不能做选择");
        }
        message.setPayloadJson(writeJson(payload));
        messageMapper.updateById(message);
        touch(conversation);
        return detail(conversation);
    }

    @Override
    @Transactional
    public ConversationDetailVO draft(Long id) {
        Conversation conversation = requireOpen(id);
        List<String> userLines = userLines(conversation.getId());
        if (userLines.isEmpty()) {
            throw BusinessException.badRequest("还没有可以整理的内容");
        }
        if (!conversationModel.available()) {
            throw BusinessException.badRequest("请先配置模型，再整理草稿");
        }
        DraftReply reply;
        try {
            reply = conversationModel.draft(draftPrompt(DraftSanitizer.storyLines(userLines)));
        } catch (RuntimeException exception) {
            log.warn("Draft generation failed", exception);
            throw BusinessException.badRequest("草稿没有生成，请再试一次");
        }
        StoredDraft draft = DraftSanitizer.sanitize(reply, userLines);
        conversation.setDraftJson(writeJson(draft));
        conversation.setStatus(DRAFTED);
        touch(conversation);
        return detail(conversation);
    }

    @Override
    @Transactional
    public CommitResult commit(Long id, ConversationCommitRequest request) {
        Conversation conversation = requireOwned(id);
        if (COMMITTED.equals(conversation.getStatus())) {
            return readResult(conversation.getResultJson());
        }
        StoredDraft draft = readDraft(conversation.getDraftJson());
        if (draft == null) {
            throw BusinessException.badRequest("请先整理草稿");
        }
        applyEdits(draft, request);
        List<ConversationMessage> messages = messages(conversation.getId());
        CommitResult result = draft.isIncident()
                ? commitIncident(draft, messages, request)
                : commitSupplements(messages);
        conversation.setDraftJson(writeJson(draft));
        conversation.setResultJson(writeJson(result));
        conversation.setCommittedDiaryId(result.getDiaryId());
        conversation.setStatus(COMMITTED);
        touch(conversation);
        return result;
    }

    private ConversationDetailVO sendText(Long id, String text) {
        Conversation conversation = requireOpen(id);
        insertMessage(conversation.getId(), USER, TurnSanitizer.INTERVIEW, text, null);
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.setTitle(Texts.truncate(text, 24));
        }
        List<RecallHit> hits = conversationRecall.find(text);
        TurnSanitizer.SanitizedTurn turn = produceTurn(conversation.getId(), text, hits);
        insertMessage(conversation.getId(), ASSISTANT, turn.kind(), turn.text(), turn.payload());
        if (DRAFTED.equals(conversation.getStatus())) {
            conversation.setStatus(ACTIVE);
        }
        touch(conversation);
        return detail(conversation);
    }

    private boolean emitVisible(SseEmitter emitter, String text) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        try {
            emitter.send(SseEmitter.event().name("token").data(writeJson(java.util.Map.of("text", text))));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void finishStream(
            SseEmitter emitter,
            AtomicBoolean finished,
            Conversation conversation,
            TurnTools tools,
            List<String> keywords,
            StringBuilder visible,
            boolean failed,
            boolean stopped) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            String text = visible.toString().strip();
            if (stopped && !StringUtils.hasText(text)) {
                completeQuietly(emitter);
                return;
            }
            MessagePayload payload = new MessagePayload();
            String kind = TurnSanitizer.INTERVIEW;
            if (stopped) {
                payload.setReady(false);
            } else if (failed) {
                text = StringUtils.hasText(text)
                        ? text
                        : "这次没有得到有效回复，你刚才的话已经留下了，可以再发一次。";
            } else {
                tools.proposeRelated(keywords, alreadyLinked(conversation.getId()));
                tools.apply(payload);
                kind = tools.kind();
                if (!StringUtils.hasText(text)) {
                    text = "我还需要你再讲一句，才能接着问。";
                    kind = TurnSanitizer.INTERVIEW;
                }
            }
            boolean offered = tools.offeredOrganize() || OrganizeReady.spokenOffer(text);
            if (!stopped) {
                payload.setReady(OrganizeReady.allow(kind, !failed && offered, userTexts(conversation.getId())));
            }
            insertMessage(conversation.getId(), ASSISTANT, kind, text, payload);
            touch(conversation);
            emitter.send(SseEmitter.event().name("done").data(writeJson(detail(conversation)), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception exception) {
            completeQuietly(emitter);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端已经断开
        }
    }

    private boolean stopped(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.util.concurrent.CancellationException) {
                return true;
            }
            String name = current.getClass().getSimpleName();
            if (name.contains("Cancel") || name.contains("Abort")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Set<Long> alreadyLinked(Long conversationId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ConversationMessage message : messages(conversationId)) {
            for (MessagePayload.LinkPayload link : readPayload(message.getPayloadJson()).getLinks()) {
                if (link.getKnowledgeId() != null) {
                    ids.add(link.getKnowledgeId());
                }
            }
        }
        return ids;
    }

    private void markReady(TurnSanitizer.SanitizedTurn turn, boolean modelReady, List<String> userTexts) {
        turn.payload().setReady(OrganizeReady.allow(turn.kind(), modelReady, userTexts));
    }

    private List<String> userTexts(Long conversationId) {
        return messages(conversationId).stream()
                .filter(message -> USER.equals(message.getRole()))
                .map(ConversationMessage::getText)
                .toList();
    }

    private TurnSanitizer.SanitizedTurn produceTurn(Long conversationId, String text, List<RecallHit> hits) {
        if (!conversationModel.available()) {
            TurnReply reply = new TurnReply();
            reply.setKind(TurnSanitizer.INTERVIEW);
            reply.setText(ConversationPrompts.unavailableText());
            return TurnSanitizer.finish(reply, List.of());
        }
        try {
            TurnReply reply = conversationModel.reply(turnPrompt(conversationId, text, hits));
            TurnSanitizer.SanitizedTurn turn = TurnSanitizer.finish(reply, hits);
            markReady(turn, reply.isReady() || OrganizeReady.spokenOffer(turn.text()), userTexts(conversationId));
            return turn;
        } catch (RuntimeException exception) {
            log.warn("Conversation model failed", exception);
            TurnReply reply = new TurnReply();
            reply.setKind(TurnSanitizer.INTERVIEW);
            reply.setText("这次没有得到有效回复，你刚才的话已经留下了，可以再发一次。");
            return TurnSanitizer.finish(reply, List.of());
        }
    }

    private CommitResult commitIncident(StoredDraft draft, List<ConversationMessage> messages, ConversationCommitRequest request) {
        if (!StringUtils.hasText(draft.getTitle()) || !StringUtils.hasText(draft.getContent())) {
            throw BusinessException.badRequest("日记标题和正文不能为空");
        }
        DiaryCreateRequest diaryRequest = new DiaryCreateRequest();
        diaryRequest.setTitle(Texts.truncate(draft.getTitle(), 200));
        diaryRequest.setContent(draft.getContent());
        diaryRequest.setSummary(Texts.truncate(draft.getSummary(), 500));
        diaryRequest.setDate(draftDate(draft));
        diaryRequest.setKind(DiaryKinds.normalize(StringUtils.hasText(draft.getKind()) ? draft.getKind() : "记录"));
        diaryRequest.setOrigin(DiaryOrigins.INTERVIEW);
        diaryRequest.setTags(draft.getTags());
        DiaryDetailVO diary = diaryService.create(diaryRequest);

        ExperienceCreateRequest experienceRequest = new ExperienceCreateRequest();
        experienceRequest.setDiaryId(diary.getId());
        experienceRequest.setTitle(Texts.truncate(draft.getTitle() + " —— 经验提炼", 200));
        experienceRequest.setProblem(blankToPending(draft.getProblem()));
        experienceRequest.setCause(blankToPending(draft.getCause()));
        experienceRequest.setSolution(blankToPending(draft.getSolution()));
        experienceRequest.setLesson(blankToPending(draft.getLesson()));
        experienceRequest.setDomain(draft.getDomain());
        experienceRequest.setTags(draft.getTags());
        experienceRequest.setKnowledgeIds(mergedIds(acceptedKnowledgeIds(messages), request == null ? null : request.getKnowledgeIds()));
        experienceRequest.setNewKnowledge(acceptedSupplements(messages));
        ExperienceDetailVO experience = experienceService.create(experienceRequest);
        experienceService.attachToDiary(diary.getId(), request == null ? null : request.getExperienceIds());

        CommitResult result = new CommitResult();
        result.setDiaryId(diary.getId());
        result.setExperienceId(experience.getId());
        result.setKnowledgeIds(experience.getKnowledgeIds());
        return result;
    }

    private CommitResult commitSupplements(List<ConversationMessage> messages) {
        List<KnowledgeDraftRequest> supplements = acceptedSupplements(messages);
        if (supplements.isEmpty()) {
            throw BusinessException.badRequest("没有你同意写入的内容");
        }
        CommitResult result = new CommitResult();
        for (KnowledgeDraftRequest supplement : supplements) {
            KnowledgeCreateRequest request = new KnowledgeCreateRequest();
            request.setTitle(supplement.getTitle());
            request.setCategory(supplement.getCategory());
            request.setDomain(supplement.getDomain());
            request.setSummary(supplement.getSummary());
            request.setContent(supplement.getContent());
            request.setTags(supplement.getTags());
            request.setMastery("待补充");
            result.getKnowledgeIds().add(knowledgeService.create(request).getId());
        }
        return result;
    }

    private List<Long> acceptedKnowledgeIds(List<ConversationMessage> messages) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ConversationMessage message : messages) {
            for (MessagePayload.LinkPayload link : readPayload(message.getPayloadJson()).getLinks()) {
                if (ACCEPT.equals(link.getChoice()) && link.getKnowledgeId() != null) {
                    ids.add(link.getKnowledgeId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private List<Long> mergedIds(List<Long> accepted, List<Long> chosen) {
        Set<Long> ids = new LinkedHashSet<>();
        if (accepted != null) {
            ids.addAll(accepted);
        }
        if (chosen != null) {
            chosen.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }

    private List<KnowledgeDraftRequest> acceptedSupplements(List<ConversationMessage> messages) {
        List<KnowledgeDraftRequest> drafts = new ArrayList<>();
        for (ConversationMessage message : messages) {
            if (!TurnSanitizer.SUPPLEMENT.equals(message.getKind())) {
                continue;
            }
            MessagePayload payload = readPayload(message.getPayloadJson());
            if (!ACCEPT.equals(payload.getChoice()) || payload.getSupplement() == null) {
                continue;
            }
            MessagePayload.SupplementPayload supplement = payload.getSupplement();
            KnowledgeDraftRequest draft = new KnowledgeDraftRequest();
            draft.setTitle(supplement.getTitle());
            draft.setDomain(supplement.getDomain());
            draft.setCategory(supplement.getCategory());
            draft.setSummary(supplement.getSummary());
            String note = "来自对话中的模型补充，经你确认后写入。";
            String content = supplement.getContent() == null ? "" : supplement.getContent();
            draft.setContent(content.contains(note) ? content : content + "\n\n" + note);
            drafts.add(draft);
        }
        return drafts;
    }

    private LocalDate draftDate(StoredDraft draft) {
        if (draft != null && StringUtils.hasText(draft.getDate())) {
            try {
                return LocalDate.parse(draft.getDate().trim());
            } catch (RuntimeException ignored) {
                return LocalDate.now();
            }
        }
        return LocalDate.now();
    }

    private void applyEdits(StoredDraft draft, ConversationCommitRequest request) {
        if (request == null) {
            return;
        }
        if (StringUtils.hasText(request.getTitle())) {
            draft.setTitle(request.getTitle().trim());
        }
        if (StringUtils.hasText(request.getDate())) {
            draft.setDate(request.getDate().trim());
        }
        if (request.getSummary() != null) {
            draft.setSummary(request.getSummary().trim());
        }
        if (request.getContent() != null) {
            draft.setContent(request.getContent());
        }
        if (StringUtils.hasText(request.getKind())) {
            draft.setKind(request.getKind().trim());
        }
        if (request.getTags() != null) {
            draft.setTags(Texts.normalizeTags(request.getTags()));
        }
        if (request.getProblem() != null) {
            draft.setProblem(request.getProblem().trim());
        }
        if (request.getCause() != null) {
            draft.setCause(request.getCause().trim());
        }
        if (request.getSolution() != null) {
            draft.setSolution(request.getSolution().trim());
        }
        if (request.getLesson() != null) {
            draft.setLesson(request.getLesson().trim());
        }
    }

    private String turnPrompt(Long conversationId, String text, List<RecallHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("已有对话：\n");
        for (ConversationMessage message : messages(conversationId)) {
            prompt.append(message.getRole()).append("：").append(message.getText()).append("\n");
        }
        prompt.append("用户刚才说：").append(text).append("\n候选记录：").append(writeJson(hits));
        return prompt.toString();
    }

    private String draftPrompt(List<String> userLines) {
        StringBuilder prompt = new StringBuilder("用户原话：\n");
        for (int i = 0; i < userLines.size(); i += 1) {
            prompt.append(i + 1).append(". ").append(userLines.get(i)).append("\n");
        }
        return prompt.toString();
    }

    private void insertMessage(Long conversationId, String role, String kind, String text, MessagePayload payload) {
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setKind(kind);
        message.setText(text);
        message.setPayloadJson(payload == null ? null : writeJson(payload));
        messageMapper.insert(message);
    }

    private Conversation requireOpen(Long id) {
        Conversation conversation = requireOwned(id);
        if (COMMITTED.equals(conversation.getStatus())) {
            throw BusinessException.badRequest("这次对话已经保存");
        }
        return conversation;
    }

    private Conversation requireOwned(Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null || !Objects.equals(conversation.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("对话不存在");
        }
        return conversation;
    }

    private ConversationMessage requireMessage(Long conversationId, Long messageId) {
        ConversationMessage message = messageMapper.selectById(messageId);
        if (message == null || !Objects.equals(message.getConversationId(), conversationId)) {
            throw BusinessException.notFound("消息不存在");
        }
        return message;
    }

    private List<ConversationMessage> messages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<ConversationMessage>lambdaQuery()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getId));
    }

    private List<String> userLines(Long conversationId) {
        return messages(conversationId).stream()
                .filter(message -> USER.equals(message.getRole()))
                .map(ConversationMessage::getText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void touch(Conversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    private ConversationDetailVO detail(Conversation conversation) {
        ConversationDetailVO vo = new ConversationDetailVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setStatus(conversation.getStatus());
        vo.setDraft(readDraft(conversation.getDraftJson()));
        vo.setCommittedDiaryId(conversation.getCommittedDiaryId());
        vo.setUpdatedAt(conversation.getUpdatedAt());
        vo.setMessages(messages(conversation.getId()).stream().map(this::toMessage).toList());
        if (vo.getDraft() != null) {
            vo.setRelated(conversationRecall.find(String.join("\n", DraftSanitizer.storyLines(userLines(conversation.getId())))));
        }
        return vo;
    }

    private ConversationListItemVO toListItem(Conversation conversation) {
        ConversationListItemVO vo = new ConversationListItemVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setStatus(conversation.getStatus());
        vo.setUpdatedAt(conversation.getUpdatedAt());
        return vo;
    }

    private ConversationMessageVO toMessage(ConversationMessage message) {
        ConversationMessageVO vo = new ConversationMessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setKind(message.getKind());
        vo.setText(message.getText());
        vo.setPayload(readPayload(message.getPayloadJson()));
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private String normalizeChoice(String choice) {
        if (ACCEPT.equals(choice) || SKIP.equals(choice)) {
            return choice;
        }
        throw BusinessException.badRequest("选择只能是同意或跳过");
    }

    private String blankToPending(String value) {
        return StringUtils.hasText(value) ? value.trim() : "待补充";
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MessagePayload readPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return new MessagePayload();
        }
        try {
            MessagePayload payload = objectMapper.readValue(json, MessagePayload.class);
            if (payload.getRefs() == null) {
                payload.setRefs(new ArrayList<>());
            }
            if (payload.getLinks() == null) {
                payload.setLinks(new ArrayList<>());
            }
            return payload;
        } catch (JsonProcessingException exception) {
            return new MessagePayload();
        }
    }

    private StoredDraft readDraft(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredDraft.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private CommitResult readResult(String json) {
        if (!StringUtils.hasText(json)) {
            return new CommitResult();
        }
        try {
            return objectMapper.readValue(json, CommitResult.class);
        } catch (JsonProcessingException exception) {
            return new CommitResult();
        }
    }
}
