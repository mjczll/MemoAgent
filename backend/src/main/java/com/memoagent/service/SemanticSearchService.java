package com.memoagent.service;

import com.memoagent.common.CurrentUser;
import com.memoagent.common.Texts;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.vector.PgVectorRepository;
import com.memoagent.vector.VectorMatch;
import com.memoagent.vector.VectorSourceTypes;
import com.memoagent.vo.SearchHitVO;
import com.memoagent.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 阶段 6 语义检索。对应开发文档第 19 节的 RAG 检索流程：
 * 向量相似度 + metadata filter（user_id / kinds）+ Top-K + 去重 + 引用回填 + Context 限制。
 * <p>
 * Hybrid Search / Rerank / Query Rewrite 是开发文档第 15 节明确后置的内容，这里不做。
 * 向量表只存向量和回指，命中后一律回 MySQL 取标题、摘要，已软删除的资产直接跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    public static final int DEFAULT_TOP_K = 10;

    private static final int MAX_TOP_K = 30;

    /** 只挡明显不相关的命中，避免完全无关的查询也塞满 Top-K；可由 minScore 参数覆盖。 */
    public static final double DEFAULT_MIN_SCORE = 0.1;

    /** 回填摘要的长度，避免把整篇正文塞进搜索结果 / 采访提示词。 */
    private static final int EXCERPT_MAX = 80;

    private final ObjectProvider<PgVectorRepository> repositoryProvider;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    public SearchResultVO search(String query, Collection<String> kinds, Integer topK, Double minScore) {
        return search(query, kinds, topK, minScore, EXCERPT_MAX);
    }

    /**
     * excerptMax 控制引用回填的长度。搜索页要短（{@link #EXCERPT_MAX}），
     * 采访里的「回顾」要能让模型据此复述上次的做法，所以要传更大的值。
     */
    public SearchResultVO search(String query, Collection<String> kinds, Integer topK, Double minScore, int excerptMax) {
        if (!StringUtils.hasText(query)) {
            return SearchResultVO.empty();
        }
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return SearchResultVO.unavailable("向量库未启用（memoagent.vector.enabled=false）或应用未连接 pgvector");
        }
        List<String> types = normalizeKinds(kinds);
        if (kinds != null && !kinds.isEmpty() && types.isEmpty()) {
            return SearchResultVO.empty();
        }
        int limit = topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(topK, MAX_TOP_K));
        double threshold = minScore == null ? DEFAULT_MIN_SCORE : minScore;
        try {
            List<VectorMatch> matches = repository.search(CurrentUser.id(), query.trim(),
                    types.isEmpty() ? null : types, limit);
            List<SearchHitVO> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (VectorMatch match : matches) {
                if (match.score() < threshold) {
                    continue;
                }
                // 一条资产一条向量（upsert 保证），这里仍做去重，防止历史脏数据重复占位
                if (!seen.add(match.sourceType() + ":" + match.sourceId())) {
                    continue;
                }
                SearchHitVO hit = hydrate(match, excerptMax);
                if (hit != null) {
                    hits.add(hit);
                }
            }
            return SearchResultVO.ok(hits);
        } catch (RuntimeException exception) {
            log.warn("语义检索失败：{}", query, exception);
            return SearchResultVO.unavailable("语义检索失败：" + exception.getMessage());
        }
    }

    private List<String> normalizeKinds(Collection<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return List.of();
        }
        return kinds.stream()
                .filter(item -> StringUtils.hasText(item))
                .map(String::trim)
                .filter(VectorSourceTypes.ALL::contains)
                .distinct()
                .toList();
    }

    /** 命中 sourceId 后回查 MySQL 组装引用；资产已软删除就跳过，不返回空壳引用。 */
    private SearchHitVO hydrate(VectorMatch match, int excerptMax) {
        String type = match.sourceType();
        String title;
        String excerpt;
        if (VectorSourceTypes.DIARY.equals(type)) {
            Diary diary = diaryMapper.selectById(match.sourceId());
            if (diary == null) {
                return null;
            }
            title = diary.getTitle();
            excerpt = StringUtils.hasText(diary.getSummary()) ? diary.getSummary() : diary.getContent();
        } else if (VectorSourceTypes.EXPERIENCE.equals(type)) {
            Experience experience = experienceMapper.selectById(match.sourceId());
            if (experience == null) {
                return null;
            }
            title = experience.getTitle();
            excerpt = StringUtils.hasText(experience.getProblem()) ? experience.getProblem() : experience.getLesson();
        } else if (VectorSourceTypes.KNOWLEDGE.equals(type)) {
            Knowledge knowledge = knowledgeMapper.selectById(match.sourceId());
            if (knowledge == null) {
                return null;
            }
            title = knowledge.getTitle();
            excerpt = StringUtils.hasText(knowledge.getSummary()) ? knowledge.getSummary() : knowledge.getContent();
        } else {
            return null;
        }
        SearchHitVO hit = new SearchHitVO();
        hit.setKind(type);
        hit.setId(match.sourceId());
        hit.setTitle(title);
        hit.setExcerpt(Texts.truncate(excerpt, excerptMax));
        hit.setScore(match.score());
        return hit;
    }
}
