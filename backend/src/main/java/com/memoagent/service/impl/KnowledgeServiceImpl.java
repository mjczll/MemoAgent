package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.Masteries;
import com.memoagent.common.Texts;
import com.memoagent.dto.KnowledgeCreateRequest;
import com.memoagent.dto.KnowledgeQuery;
import com.memoagent.dto.KnowledgeUpdateRequest;
import com.memoagent.entity.DiaryKnowledge;
import com.memoagent.entity.Experience;
import com.memoagent.entity.ExperienceKnowledge;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.KnowledgeRelated;
import com.memoagent.entity.KnowledgeTag;
import com.memoagent.entity.Tag;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryKnowledgeMapper;
import com.memoagent.mapper.ExperienceKnowledgeMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.mapper.KnowledgeRelatedMapper;
import com.memoagent.mapper.KnowledgeTagMapper;
import com.memoagent.mapper.TagMapper;
import com.memoagent.service.KnowledgeService;
import com.memoagent.vo.KnowledgeDetailVO;
import com.memoagent.vo.KnowledgeListItemVO;
import com.memoagent.vo.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String PRIVATE = "private";
    private static final String DEFAULT_DOMAIN = "项目开发";

    private final KnowledgeMapper knowledgeMapper;
    private final TagMapper tagMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final KnowledgeRelatedMapper knowledgeRelatedMapper;
    private final ExperienceKnowledgeMapper experienceKnowledgeMapper;
    private final ExperienceMapper experienceMapper;
    private final DiaryKnowledgeMapper diaryKnowledgeMapper;

    @Override
    @Transactional
    public KnowledgeDetailVO create(KnowledgeCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Knowledge knowledge = new Knowledge();
        knowledge.setUserId(CurrentUser.id());
        applyFields(knowledge, request.getTitle(), request.getCategory(), request.getDomain(),
                request.getSummary(), request.getContent(), request.getMastery());
        knowledge.setVisibility(PRIVATE);
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        knowledgeMapper.insert(knowledge);

        List<String> tags = replaceTags(knowledge.getId(), request.getTags());
        List<Long> relatedIds = replaceRelated(knowledge.getId(), request.getRelatedIds());
        List<Long> sourceIds = replaceSources(knowledge.getId(), request.getSourceExperienceIds());
        return toDetail(knowledge, tags, relatedIds, sourceIds);
    }

    @Override
    public PageData<KnowledgeListItemVO> list(KnowledgeQuery query) {
        normalizeQuery(query);
        Page<Knowledge> page = new Page<>(query.getPage(), query.getSize());
        var wrapper = Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getUserId, CurrentUser.id());
        if (StringUtils.hasText(query.getDomain()) && !"all".equalsIgnoreCase(query.getDomain())) {
            wrapper.eq(Knowledge::getDomain, query.getDomain().trim());
        }
        if (StringUtils.hasText(query.getMastery()) && !"all".equalsIgnoreCase(query.getMastery())) {
            wrapper.eq(Knowledge::getMastery, Masteries.normalize(query.getMastery()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(Knowledge::getTitle, keyword)
                    .or().like(Knowledge::getSummary, keyword)
                    .or().like(Knowledge::getContent, keyword)
                    .or().like(Knowledge::getCategory, keyword));
        }
        wrapper.orderByDesc(Knowledge::getUpdatedAt).orderByDesc(Knowledge::getId);

        Page<Knowledge> result = knowledgeMapper.selectPage(page, wrapper);
        List<Long> ids = result.getRecords().stream().map(Knowledge::getId).toList();
        Map<Long, List<String>> tags = loadTagNames(ids);
        Map<Long, List<Long>> related = loadRelatedIds(ids);
        Map<Long, List<Long>> sources = loadSourceIds(ids);
        List<KnowledgeListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item,
                        tags.getOrDefault(item.getId(), List.of()),
                        related.getOrDefault(item.getId(), List.of()),
                        sources.getOrDefault(item.getId(), List.of())))
                .toList();
        return new PageData<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public KnowledgeDetailVO get(Long id) {
        Knowledge knowledge = requireOwned(id);
        return toDetail(
                knowledge,
                loadTagNames(List.of(id)).getOrDefault(id, List.of()),
                loadRelatedIds(List.of(id)).getOrDefault(id, List.of()),
                loadSourceIds(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override
    @Transactional
    public KnowledgeDetailVO update(Long id, KnowledgeUpdateRequest request) {
        Knowledge knowledge = requireOwned(id);
        applyFields(knowledge, request.getTitle(), request.getCategory(), request.getDomain(),
                request.getSummary(), request.getContent(), request.getMastery());
        knowledge.setUpdatedAt(LocalDateTime.now());
        knowledgeMapper.updateById(knowledge);

        List<String> tags = replaceTags(id, request.getTags());
        List<Long> relatedIds = request.getRelatedIds() == null
                ? loadRelatedIds(List.of(id)).getOrDefault(id, List.of())
                : replaceRelated(id, request.getRelatedIds());
        List<Long> sourceIds = request.getSourceExperienceIds() == null
                ? loadSourceIds(List.of(id)).getOrDefault(id, List.of())
                : replaceSources(id, request.getSourceExperienceIds());
        return toDetail(knowledge, tags, relatedIds, sourceIds);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        knowledgeTagMapper.delete(Wrappers.<KnowledgeTag>lambdaQuery().eq(KnowledgeTag::getKnowledgeId, id));
        knowledgeRelatedMapper.delete(Wrappers.<KnowledgeRelated>lambdaQuery()
                .eq(KnowledgeRelated::getKnowledgeId, id)
                .or().eq(KnowledgeRelated::getRelatedId, id));
        experienceKnowledgeMapper.delete(Wrappers.<ExperienceKnowledge>lambdaQuery()
                .eq(ExperienceKnowledge::getKnowledgeId, id));
        diaryKnowledgeMapper.delete(Wrappers.<DiaryKnowledge>lambdaQuery()
                .eq(DiaryKnowledge::getKnowledgeId, id));
        knowledgeMapper.deleteById(id);
    }

    private void applyFields(Knowledge knowledge, String title, String category, String domain,
                             String summary, String content, String mastery) {
        knowledge.setTitle(title.trim());
        knowledge.setCategory(StringUtils.hasText(category) ? category.trim() : "未分类");
        knowledge.setDomain(StringUtils.hasText(domain) ? domain.trim() : DEFAULT_DOMAIN);
        knowledge.setContent(content);
        knowledge.setSummary(StringUtils.hasText(summary)
                ? Texts.truncate(summary, 500)
                : Texts.truncate(content, 60));
        knowledge.setMastery(Masteries.normalize(mastery));
    }

    private Knowledge requireOwned(Long id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null || !Objects.equals(knowledge.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("知识不存在");
        }
        return knowledge;
    }

    private List<String> replaceTags(Long knowledgeId, List<String> rawTags) {
        List<String> names = Texts.normalizeTags(rawTags);
        knowledgeTagMapper.delete(Wrappers.<KnowledgeTag>lambdaQuery().eq(KnowledgeTag::getKnowledgeId, knowledgeId));
        for (String name : names) {
            KnowledgeTag relation = new KnowledgeTag();
            relation.setKnowledgeId(knowledgeId);
            relation.setTagId(findOrCreateTag(name).getId());
            knowledgeTagMapper.insert(relation);
        }
        return names;
    }

    private List<Long> replaceRelated(Long knowledgeId, List<Long> relatedIds) {
        knowledgeRelatedMapper.delete(Wrappers.<KnowledgeRelated>lambdaQuery()
                .eq(KnowledgeRelated::getKnowledgeId, knowledgeId));
        List<Long> result = new ArrayList<>();
        if (relatedIds == null) {
            return result;
        }
        for (Long relatedId : relatedIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (Objects.equals(relatedId, knowledgeId)) {
                continue;
            }
            Knowledge related = knowledgeMapper.selectById(relatedId);
            if (related == null || !Objects.equals(related.getUserId(), CurrentUser.id())) {
                throw BusinessException.badRequest("互链知识不存在");
            }
            KnowledgeRelated row = new KnowledgeRelated();
            row.setKnowledgeId(knowledgeId);
            row.setRelatedId(relatedId);
            knowledgeRelatedMapper.insert(row);
            result.add(relatedId);
        }
        return result;
    }

    private List<Long> replaceSources(Long knowledgeId, List<Long> sourceIds) {
        experienceKnowledgeMapper.delete(Wrappers.<ExperienceKnowledge>lambdaQuery()
                .eq(ExperienceKnowledge::getKnowledgeId, knowledgeId));
        diaryKnowledgeMapper.delete(Wrappers.<DiaryKnowledge>lambdaQuery()
                .eq(DiaryKnowledge::getKnowledgeId, knowledgeId));
        List<Long> result = new ArrayList<>();
        if (sourceIds == null) {
            return result;
        }
        for (Long experienceId : sourceIds.stream().filter(Objects::nonNull).distinct().toList()) {
            Experience experience = experienceMapper.selectById(experienceId);
            if (experience == null || !Objects.equals(experience.getUserId(), CurrentUser.id())) {
                throw BusinessException.badRequest("来源经验不存在");
            }
            ExperienceKnowledge link = new ExperienceKnowledge();
            link.setExperienceId(experienceId);
            link.setKnowledgeId(knowledgeId);
            experienceKnowledgeMapper.insert(link);
            if (experience.getDiaryId() != null) {
                DiaryKnowledge diaryLink = new DiaryKnowledge();
                diaryLink.setDiaryId(experience.getDiaryId());
                diaryLink.setKnowledgeId(knowledgeId);
                diaryKnowledgeMapper.insert(diaryLink);
            }
            result.add(experienceId);
        }
        return result;
    }

    private Tag findOrCreateTag(String name) {
        Tag existing = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name).last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType("custom");
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        return tag;
    }

    private Map<Long, List<String>> loadTagNames(List<Long> knowledgeIds) {
        if (knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeTag> relations = knowledgeTagMapper.selectList(
                Wrappers.<KnowledgeTag>lambdaQuery().in(KnowledgeTag::getKnowledgeId, knowledgeIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameById = tagMapper.selectByIds(relations.stream().map(KnowledgeTag::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (KnowledgeTag relation : relations) {
            String name = nameById.get(relation.getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getKnowledgeId(), key -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private Map<Long, List<Long>> loadRelatedIds(List<Long> knowledgeIds) {
        if (knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeRelated> relations = knowledgeRelatedMapper.selectList(
                Wrappers.<KnowledgeRelated>lambdaQuery().in(KnowledgeRelated::getKnowledgeId, knowledgeIds));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (KnowledgeRelated relation : relations) {
            result.computeIfAbsent(relation.getKnowledgeId(), key -> new ArrayList<>()).add(relation.getRelatedId());
        }
        return result;
    }

    private Map<Long, List<Long>> loadSourceIds(List<Long> knowledgeIds) {
        if (knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<ExperienceKnowledge> relations = experienceKnowledgeMapper.selectList(
                Wrappers.<ExperienceKnowledge>lambdaQuery().in(ExperienceKnowledge::getKnowledgeId, knowledgeIds));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (ExperienceKnowledge relation : relations) {
            result.computeIfAbsent(relation.getKnowledgeId(), key -> new ArrayList<>()).add(relation.getExperienceId());
        }
        return result;
    }

    private void normalizeQuery(KnowledgeQuery query) {
        if (query.getPage() < 1) {
            query.setPage(1);
        }
        if (query.getSize() < 1) {
            query.setSize(20);
        }
        if (query.getSize() > 100) {
            query.setSize(100);
        }
    }

    private KnowledgeDetailVO toDetail(Knowledge knowledge, List<String> tags, List<Long> relatedIds, List<Long> sourceIds) {
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(knowledge.getId());
        vo.setTitle(knowledge.getTitle());
        vo.setCategory(knowledge.getCategory());
        vo.setDomain(knowledge.getDomain());
        vo.setTags(tags);
        vo.setSummary(knowledge.getSummary());
        vo.setContent(knowledge.getContent());
        vo.setRelatedIds(relatedIds);
        vo.setSourceExperienceIds(sourceIds);
        vo.setMastery(knowledge.getMastery());
        vo.setVisibility(PRIVATE);
        vo.setCreatedAt(knowledge.getCreatedAt());
        vo.setUpdatedAt(knowledge.getUpdatedAt());
        return vo;
    }

    private KnowledgeListItemVO toListItem(Knowledge knowledge, List<String> tags, List<Long> relatedIds, List<Long> sourceIds) {
        KnowledgeListItemVO vo = new KnowledgeListItemVO();
        vo.setId(knowledge.getId());
        vo.setTitle(knowledge.getTitle());
        vo.setCategory(knowledge.getCategory());
        vo.setDomain(knowledge.getDomain());
        vo.setTags(tags);
        vo.setSummary(knowledge.getSummary());
        vo.setRelatedIds(relatedIds);
        vo.setSourceExperienceIds(sourceIds);
        vo.setMastery(knowledge.getMastery());
        vo.setVisibility(PRIVATE);
        vo.setUpdatedAt(knowledge.getUpdatedAt());
        return vo;
    }
}
