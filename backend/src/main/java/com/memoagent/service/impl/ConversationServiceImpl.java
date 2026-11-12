package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoagent.ai.KnowledgeCandidate;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.DiaryKinds;
import com.memoagent.common.DiaryOrigins;
import com.memoagent.common.Texts;
import com.memoagent.conversation.CommitResult;
import com.memoagent.conversation.ConversationModel;
import com.memoagent.conversation.ConversationPrompts;
import com.memoagent.conversation.ConversationRecall;
import com.memoagent.conversation.ConversationTitleService;
import com.memoagent.conversation.DraftReply;
import com.memoagent.conversation.DraftSanitizer;
import com.memoagent.conversation.KnowledgeCandidateSanitizer;
import com.memoagent.conversation.KnowledgeResponse;
import com.memoagent.conversation.MessagePayload;
import com.memoagent.conversation.OrganizeReady;
import com.memoagent.conversation.RecallHit;
import com.memoagent.conversation.TurnTools;
import com.memoagent.conversation.StoredDraft;
import com.memoagent.conversation.StoredKnowledgeCandidate;
import com.memoagent.conversation.StreamTextCleaner;
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
import com.memoagent.vector.VectorSourceTypes;
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
    private static final String DEFAULT_TITLE = ConversationTitleService.PLACEHOLDER;

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationModel conversationModel;
    private final ConversationRecall conversationRecall;
    private final ConversationTitleService conversationTitleService;
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
                        // 置顶优先，其余按最近更新
                        .orderByDesc(Conversation::getPinned)
                        .orderByDesc(Conversation::getUpdatedAt)
                        .orderByDesc(Conversation::getId)
                        .last("limit 50"))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Override
    @Transactional
    public ConversationListItemVO rename(Long id, String title) {
        Conversation conversation = requireOwned(id);
        if (!StringUtils.hasText(title)) {
            throw BusinessException.badRequest("标题不能为空");
        }
        conversation.setTitle(Texts.truncate(title.trim(), 200));
        // 刻意不刷新 updatedAt：改名不该让这条对话跳到「最近」最前面
        conversationMapper.updateById(conversation);
        return toListItem(conversation);
    }

    @Override
    @Transactional
    public ConversationListItemVO pin(Long id, boolean pinned) {
        Conversation conversation = requireOwned(id);
        conversation.setPinned(pinned);
        conversationMapper.updateById(conversation);
        return toListItem(conversation);
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
        return runTurn(conversation, text);
    }

    /**
     * 重新生成：保留最后一条用户消息，删掉它之后的助手回复，再用同一句话跑一轮。
     * turnPrompt 会把「当前这句话」从历史里跳过，所以不会出现重复两遍。
     */
    @Override
    public SseEmitter regenerate(Long id) {
        Conversation conversation = requireOpen(id);
        ConversationMessage lastUser = messages(id).stream()
                .filter(message -> USER.equals(message.getRole()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> BusinessException.badRequest("还没有可以重新生成的内容"));
        dropAfter(id, lastUser.getId());
        return runTurn(conversation, lastUser.getText());
    }

    @Override
    public SseEmitter editStream(Long id, Long messageId, String text) {
        Conversation conversation = requireOpen(id);
        ConversationMessage message = messageMapper.selectById(messageId);
        if (message == null || !id.equals(message.getConversationId()) || !USER.equals(message.getRole())) {
            throw BusinessException.notFound("消息不存在");
        }
        if (!StringUtils.hasText(text)) {
            throw BusinessException.badRequest("内容不能为空");
        }
        message.setText(text.trim());
        messageMapper.updateById(message);
        // 改写历史后，之后的问答全部作废——和多数聊天工具的行为一致
        dropAfter(id, messageId);
        return runTurn(conversation, text.trim());
    }

    /** 删掉某条消息之后的所有消息（含助手回复）。消息 id 自增，按 id 比较即可。 */
    private void dropAfter(Long conversationId, Long messageId) {
        messageMapper.delete(Wrappers.<ConversationMessage>lambdaQuery()
                .eq(ConversationMessage::getConversationId, conversationId)
                .gt(ConversationMessage::getId, messageId));
    }

    /** 一轮问答的流式执行：召回、拼提示词、调模型、收尾落库。新发、重生成、改后重发共用。 */
    private SseEmitter runTurn(Conversation conversation, String text) {
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
                        // visible 保留原始内容，收尾时统一清洗；推到前端的先剥掉工具标记
                        visible.append(token);
                        String safe = StreamTextCleaner.cleanChunk(token);
                        if (StringUtils.hasText(safe) && !emitVisible(emitter, safe)) {
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
        List<String> storyLines = DraftSanitizer.storyLines(userLines);
        String prompt = draftPrompt(storyLines);
        DraftReply reply;
        try {
            reply = conversationModel.draft(prompt);
        } catch (RuntimeException exception) {
            log.warn("Draft generation failed", exception);
            throw BusinessException.badRequest("草稿没有生成，请再试一次");
        }
        StoredDraft draft = DraftSanitizer.sanitize(reply, userLines);
        draft.setKnowledgeCandidates(storyLines.isEmpty()
                ? List.<StoredKnowledgeCandidate>of()
                : KnowledgeCandidateSanitizer.sanitize(knowledgeCandidates(prompt), storyLines));
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
                : commitSupplements(messages, request);
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
        List<RecallHit> hits = conversationRecall.find(text);
        TurnSanitizer.SanitizedTurn turn = produceTurn(conversation.getId(), text, hits);
        insertMessage(conversation.getId(), ASSISTANT, turn.kind(),
                StreamTextCleaner.clean(turn.text()), turn.payload());
        if (DRAFTED.equals(conversation.getStatus())) {
            conversation.setStatus(ACTIVE);
        }
        touch(conversation);
        offerTitle(conversation);
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
            String text = StreamTextCleaner.clean(visible.toString().strip());
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
            offerTitle(conversation);
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
        experienceRequest.setNewKnowledge(mergedKnowledge(acceptedSupplements(messages), request));
        ExperienceDetailVO experience = experienceService.create(experienceRequest);
        experienceService.attachToDiary(diary.getId(), request == null ? null : request.getExperienceIds());

        CommitResult result = new CommitResult();
        result.setDiaryId(diary.getId());
        result.setExperienceId(experience.getId());
        result.setKnowledgeIds(experience.getKnowledgeIds());
        return result;
    }

    private CommitResult commitSupplements(List<ConversationMessage> messages, ConversationCommitRequest request) {
        List<KnowledgeDraftRequest> drafts = mergedKnowledge(acceptedSupplements(messages), request);
        if (drafts.isEmpty()) {
            throw BusinessException.badRequest("没有你同意写入的内容");
        }
        CommitResult result = new CommitResult();
        for (KnowledgeDraftRequest draft : drafts) {
            KnowledgeCreateRequest create = new KnowledgeCreateRequest();
            create.setTitle(draft.getTitle());
            create.setCategory(draft.getCategory());
            create.setDomain(draft.getDomain());
            create.setSummary(draft.getSummary());
            create.setContent(draft.getContent());
            create.setTags(draft.getTags());
            create.setMastery("待补充");
            result.getKnowledgeIds().add(knowledgeService.create(create).getId());
        }
        return result;
    }

    /** 草稿页勾选的知识候选。没勾的不回传，回传的才算数。 */
    private List<KnowledgeDraftRequest> chosenKnowledge(ConversationCommitRequest request) {
        if (request == null || request.getKnowledgeDrafts() == null) {
            return List.of();
        }
        List<KnowledgeDraftRequest> chosen = new ArrayList<>();
        for (KnowledgeDraftRequest item : request.getKnowledgeDrafts()) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            chosen.add(item);
        }
        return chosen;
    }

    private List<KnowledgeDraftRequest> mergedKnowledge(List<KnowledgeDraftRequest> fromMessages, ConversationCommitRequest request) {
        List<KnowledgeDraftRequest> merged = new ArrayList<>(fromMessages);
        merged.addAll(chosenKnowledge(request));
        return merged;
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
        List<ConversationMessage> history = messages(conversationId);
        // 调用方已经把当前这句话插进库了，如果这里再原样列进「已有对话」，
        // 提示词里同一句话会出现两次（历史末尾 + 下面的「用户刚才说」），
        // 模型容易把它当成两轮重复输入，从而复述上一轮的答案。
        int currentIndex = -1;
        for (int index = history.size() - 1; index >= 0; index -= 1) {
            ConversationMessage message = history.get(index);
            if (USER.equals(message.getRole()) && text.equals(message.getText())) {
                currentIndex = index;
                break;
            }
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("已有对话：\n");
        for (int index = 0; index < history.size(); index += 1) {
            if (index == currentIndex) {
                continue;
            }
            ConversationMessage message = history.get(index);
            prompt.append(message.getRole()).append("：").append(message.getText()).append("\n");
        }
        prompt.append("用户刚才说：").append(text).append("\n");
        prompt.append("候选记录（用户过去写过的日记、经验和知识，按相关度从高到低）：\n");
        if (hits == null || hits.isEmpty()) {
            prompt.append("（无）\n");
        } else {
            int order = 1;
            for (RecallHit hit : hits) {
                prompt.append(order).append(". ").append(typeLabel(hit.type()))
                        .append(" #").append(hit.id()).append("｜").append(hit.title());
                if (StringUtils.hasText(hit.excerpt())) {
                    prompt.append("｜").append(hit.excerpt());
                }
                prompt.append("\n");
                order += 1;
            }
        }
        return prompt.toString();
    }

    private String typeLabel(String type) {
        if (VectorSourceTypes.DIARY.equals(type)) {
            return "日记";
        }
        if (VectorSourceTypes.EXPERIENCE.equals(type)) {
            return "经验";
        }
        if (VectorSourceTypes.KNOWLEDGE.equals(type)) {
            return "知识";
        }
        return type;
    }

    private String draftPrompt(List<String> userLines) {
        StringBuilder prompt = new StringBuilder("用户原话：\n");
        for (int i = 0; i < userLines.size(); i += 1) {
            prompt.append(i + 1).append(". ").append(userLines.get(i)).append("\n");
        }
        return prompt.toString();
    }

    /** 知识候选判定失败只丢候选，不影响草稿本身。 */
    private List<KnowledgeCandidate> knowledgeCandidates(String prompt) {
        try {
            KnowledgeResponse response = conversationModel.knowledge(prompt);
            if (response == null || response.getKnowledgeCandidates() == null) {
                return List.of();
            }
            return response.getKnowledgeCandidates();
        } catch (RuntimeException exception) {
            log.warn("Knowledge candidate generation failed", exception);
            return List.of();
        }
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

    /**
     * 标题仍是占位符时才让模型起名。异步执行，不影响本轮响应；
     * 失败就保持「新对话」，下一轮会再试一次。
     */
    private void offerTitle(Conversation conversation) {
        if (!DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }
        conversationTitleService.generate(conversation.getId());
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
        vo.setPinned(Boolean.TRUE.equals(conversation.getPinned()));
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
