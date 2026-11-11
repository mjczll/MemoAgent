package com.memoagent.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.DiaryOrigins;
import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.KnowledgeDraftRequest;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryCandidate;
import com.memoagent.entity.Experience;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryCandidateMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.service.ExperienceService;
import com.memoagent.vo.DiaryCandidateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 手写日记的经验候选：保存后异步提取、详情页确认后才落成真正的经验。
 * <p>
 * 三条硬规则：
 * 1. 不自动写入 experience —— 经验库只能由用户确认后增加；
 * 2. 幂等 —— 同一篇日记已有候选或已有经验就不再提取，重复保存不会堆候选；
 * 3. 采访生成的日记不参与 —— 整理阶段已经产出经验，再提一遍是重复劳动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryCandidateService {

    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String IGNORED = "ignored";

    private final DiaryCandidateMapper candidateMapper;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final DiaryAiService diaryAiService;
    private final ExperienceService experienceService;
    private final ObjectMapper objectMapper;

    /**
     * 自动提取。跑在异步线程里，任何异常都只记日志——
     * 提取失败不该影响用户已经保存好的日记，他仍然可以手动点「提炼经验」。
     */
    public void generate(long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);
        if (diary == null) {
            return;
        }
        if (DiaryOrigins.INTERVIEW.equals(diary.getOrigin())) {
            log.debug("日记 #{} 来自采访，已有经验，跳过自动提取", diaryId);
            return;
        }
        if (hasCandidate(diaryId)) {
            log.debug("日记 #{} 已有候选，跳过自动提取", diaryId);
            return;
        }
        if (hasExperience(diaryId)) {
            log.debug("日记 #{} 已有经验，跳过自动提取", diaryId);
            return;
        }
        try {
            ExperienceExtraction extraction = diaryAiService.extract(diaryId);
            List<ExperienceCandidate> items = extraction == null ? List.of() : extraction.getExperiences();
            if (items.isEmpty()) {
                log.info("日记 #{} 自动提取：没有可沉淀的经验", diaryId);
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            long userId = diary.getUserId() == null ? CurrentUser.id() : diary.getUserId();
            int saved = 0;
            for (ExperienceCandidate item : items) {
                if (item == null || !StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getProblem())) {
                    continue;
                }
                candidateMapper.insert(row(userId, diaryId, item, now));
                saved += 1;
            }
            log.info("日记 #{} 自动提取经验候选 {} 条，等待确认", diaryId, saved);
        } catch (RuntimeException exception) {
            log.warn("日记 #{} 自动提取经验候选失败，可手动点「提炼经验」重试", diaryId, exception);
        }
    }

    public List<DiaryCandidateVO> listPending(Long diaryId) {
        return candidateMapper.selectList(Wrappers.<DiaryCandidate>lambdaQuery()
                        .eq(DiaryCandidate::getDiaryId, diaryId)
                        .eq(DiaryCandidate::getStatus, PENDING)
                        .orderByAsc(DiaryCandidate::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    /** 采纳：建经验 + 标记已处理，同一事务，避免出现「经验建了但候选还在」。 */
    @Transactional
    public Long accept(long candidateId) {
        DiaryCandidate row = require(candidateId);
        ExperienceCreateRequest request = new ExperienceCreateRequest();
        request.setDiaryId(row.getDiaryId());
        request.setTitle(row.getTitle());
        request.setProblem(row.getProblem());
        request.setCause(row.getCause());
        request.setSolution(row.getSolution());
        request.setLesson(row.getLesson());
        request.setDomain(row.getDomain());
        request.setTags(readTags(row.getTagsJson()));
        KnowledgeDraftRequest knowledge = readKnowledge(row.getKnowledgeJson());
        if (knowledge != null) {
            request.setNewKnowledge(List.of(knowledge));
        }
        Long experienceId = experienceService.create(request).getId();
        mark(row, ACCEPTED);
        log.info("候选 #{} 已采纳为经验 #{}", candidateId, experienceId);
        return experienceId;
    }

    public void ignore(long candidateId) {
        DiaryCandidate row = require(candidateId);
        mark(row, IGNORED);
    }

    private DiaryCandidate require(long candidateId) {
        DiaryCandidate row = candidateMapper.selectById(candidateId);
        if (row == null) {
            throw BusinessException.notFound("候选不存在");
        }
        if (!PENDING.equals(row.getStatus())) {
            throw BusinessException.badRequest("这条候选已经处理过了");
        }
        return row;
    }

    private void mark(DiaryCandidate row, String status) {
        row.setStatus(status);
        row.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(row);
    }

    private boolean hasCandidate(long diaryId) {
        return candidateMapper.selectCount(Wrappers.<DiaryCandidate>lambdaQuery()
                .eq(DiaryCandidate::getDiaryId, diaryId)) > 0;
    }

    private boolean hasExperience(long diaryId) {
        return experienceMapper.selectCount(Wrappers.<Experience>lambdaQuery()
                .eq(Experience::getDiaryId, diaryId)) > 0;
    }

    private DiaryCandidate row(long userId, long diaryId, ExperienceCandidate item, LocalDateTime now) {
        DiaryCandidate row = new DiaryCandidate();
        row.setUserId(userId);
        row.setDiaryId(diaryId);
        row.setTitle(item.getTitle().trim());
        row.setProblem(item.getProblem());
        row.setCause(item.getCause());
        row.setSolution(item.getSolution());
        row.setLesson(item.getLesson());
        row.setDomain(StringUtils.hasText(item.getDomain()) ? item.getDomain().trim() : "未分类");
        row.setTagsJson(writeJson(item.getTags()));
        KnowledgeDraftRequest knowledge = toDraft(item.getKnowledge());
        row.setKnowledgeJson(knowledge == null ? null : writeJson(knowledge));
        row.setStatus(PENDING);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    /** 只保留知识创建需要的字段，避免把 evidence 等模型内部字段写进快照。 */
    private KnowledgeDraftRequest toDraft(KnowledgeCandidate source) {
        if (source == null || !StringUtils.hasText(source.getTitle()) || !StringUtils.hasText(source.getContent())) {
            return null;
        }
        KnowledgeDraftRequest draft = new KnowledgeDraftRequest();
        draft.setTitle(source.getTitle());
        draft.setCategory(source.getCategory());
        draft.setDomain(source.getDomain());
        draft.setSummary(source.getSummary());
        draft.setContent(source.getContent());
        draft.setTags(source.getTags());
        return draft;
    }

    private DiaryCandidateVO toVO(DiaryCandidate row) {
        DiaryCandidateVO vo = new DiaryCandidateVO();
        vo.setId(row.getId());
        vo.setDiaryId(row.getDiaryId());
        vo.setTitle(row.getTitle());
        vo.setProblem(row.getProblem());
        vo.setCause(row.getCause());
        vo.setSolution(row.getSolution());
        vo.setLesson(row.getLesson());
        vo.setDomain(row.getDomain());
        vo.setTags(readTags(row.getTagsJson()));
        vo.setKnowledge(readKnowledge(row.getKnowledgeJson()));
        return vo;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("候选序列化失败", exception);
            return null;
        }
    }

    private List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("候选标签解析失败：{}", json);
            return new ArrayList<>();
        }
    }

    private KnowledgeDraftRequest readKnowledge(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, KnowledgeDraftRequest.class);
        } catch (JsonProcessingException exception) {
            log.warn("候选知识解析失败：{}", json);
            return null;
        }
    }
}
