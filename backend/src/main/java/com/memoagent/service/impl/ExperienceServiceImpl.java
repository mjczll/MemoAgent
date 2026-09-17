package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.Masteries;
import com.memoagent.common.Texts;
import com.memoagent.dto.ExperienceCreateRequest;
import com.memoagent.dto.ExperienceQuery;
import com.memoagent.dto.ExperienceUpdateRequest;
import com.memoagent.dto.KnowledgeDraftRequest;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryExperience;
import com.memoagent.entity.DiaryKnowledge;
import com.memoagent.entity.Experience;
import com.memoagent.entity.ExperienceKnowledge;
import com.memoagent.entity.ExperienceTag;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.KnowledgeRelated;
import com.memoagent.entity.KnowledgeTag;
import com.memoagent.entity.Tag;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryExperienceMapper;
import com.memoagent.mapper.DiaryKnowledgeMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceKnowledgeMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.ExperienceTagMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.mapper.KnowledgeRelatedMapper;
import com.memoagent.mapper.KnowledgeTagMapper;
import com.memoagent.mapper.TagMapper;
import com.memoagent.service.ExperienceService;
import com.memoagent.vo.ExperienceDetailVO;
import com.memoagent.vo.ExperienceListItemVO;
import com.memoagent.vo.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private static final String PRIVATE = "private";
    private static final String DEFAULT_DOMAIN = "项目开发";

    private final ExperienceMapper experienceMapper;
    private final DiaryMapper diaryMapper;
    private final TagMapper tagMapper;
    private final ExperienceTagMapper experienceTagMapper;
    private final DiaryExperienceMapper diaryExperienceMapper;
    private final ExperienceKnowledgeMapper experienceKnowledgeMapper;
    private final DiaryKnowledgeMapper diaryKnowledgeMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final KnowledgeRelatedMapper knowledgeRelatedMapper;

    @Override
    @Transactional
    public ExperienceDetailVO create(ExperienceCreateRequest request) {
        Diary diary = requireDiary(request.getDiaryId());
        LocalDateTime now = LocalDateTime.now();

        Experience experience = new Experience();
        experience.setUserId(CurrentUser.id());
        experience.setDiaryId(diary.getId());
        experience.setTitle(request.getTitle().trim());
        experience.setProblem(request.getProblem().trim());
        experience.setCause(blankToEmpty(request.getCause()));
        experience.setSolution(blankToEmpty(request.getSolution()));
        experience.setLesson(blankToEmpty(request.getLesson()));
        experience.setDomain(normalizeDomain(request.getDomain()));
        experience.setVisibility(PRIVATE);
        experience.setCreatedAt(now);
        experience.setUpdatedAt(now);
        experienceMapper.insert(experience);

        linkDiary(diary.getId(), experience.getId());
        List<String> tags = replaceTags(experience.getId(), request.getTags());
        List<Long> knowledgeIds = new ArrayList<>(ownedKnowledgeIds(request.getKnowledgeIds()));
        for (KnowledgeDraftRequest draft : request.getNewKnowledge() == null ? List.<KnowledgeDraftRequest>of() : request.getNewKnowledge()) {
            knowledgeIds.add(createSuggestedKnowledge(diary, experience, draft, now));
        }
        replaceKnowledgeLinks(diary.getId(), experience.getId(), knowledgeIds);
        return toDetail(experience, diary.getTitle(), tags, knowledgeIds);
    }

    @Override
    public PageData<ExperienceListItemVO> list(ExperienceQuery query) {
        normalizeQuery(query);
        Page<Experience> page = new Page<>(query.getPage(), query.getSize());
        var wrapper = Wrappers.<Experience>lambdaQuery().eq(Experience::getUserId, CurrentUser.id());
        if (StringUtils.hasText(query.getDomain()) && !"all".equalsIgnoreCase(query.getDomain())) {
            wrapper.eq(Experience::getDomain, query.getDomain().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(Experience::getTitle, keyword)
                    .or().like(Experience::getProblem, keyword)
                    .or().like(Experience::getCause, keyword)
                    .or().like(Experience::getSolution, keyword)
                    .or().like(Experience::getLesson, keyword));
        }
        wrapper.orderByDesc(Experience::getCreatedAt).orderByDesc(Experience::getId);

        Page<Experience> result = experienceMapper.selectPage(page, wrapper);
        List<Long> ids = result.getRecords().stream().map(Experience::getId).toList();
        Map<Long, List<String>> tags = loadTagNames(ids);
        Map<Long, List<Long>> knowledgeIds = loadKnowledgeIds(ids);
        List<ExperienceListItemVO> records = result.getRecords().stream()
                .map(item -> toListItem(item, tags.getOrDefault(item.getId(), List.of()),
                        knowledgeIds.getOrDefault(item.getId(), List.of())))
                .toList();
        return new PageData<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ExperienceDetailVO get(Long id) {
        Experience experience = requireOwned(id);
        Diary diary = diaryMapper.selectById(experience.getDiaryId());
        return toDetail(
                experience,
                diary == null ? null : diary.getTitle(),
                loadTagNames(List.of(id)).getOrDefault(id, List.of()),
                loadKnowledgeIds(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override
    @Transactional
    public ExperienceDetailVO update(Long id, ExperienceUpdateRequest request) {
        Experience experience = requireOwned(id);
        experience.setTitle(request.getTitle().trim());
        experience.setProblem(request.getProblem().trim());
        experience.setCause(blankToEmpty(request.getCause()));
        experience.setSolution(blankToEmpty(request.getSolution()));
        experience.setLesson(blankToEmpty(request.getLesson()));
        experience.setDomain(normalizeDomain(request.getDomain()));
        experience.setUpdatedAt(LocalDateTime.now());
        experienceMapper.updateById(experience);

        List<String> tags = replaceTags(id, request.getTags());
        List<Long> knowledgeIds = ownedKnowledgeIds(request.getKnowledgeIds());
        replaceKnowledgeLinks(experience.getDiaryId(), id, knowledgeIds);
        Diary diary = diaryMapper.selectById(experience.getDiaryId());
        return toDetail(experience, diary == null ? null : diary.getTitle(), tags, knowledgeIds);
    }

    private Diary requireDiary(Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);
        if (diary == null || !Objects.equals(diary.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("来源日记不存在");
        }
        return diary;
    }

    private Experience requireOwned(Long id) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null || !Objects.equals(experience.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("经验不存在");
        }
        return experience;
    }

    private void linkDiary(Long diaryId, Long experienceId) {
        DiaryExperience relation = new DiaryExperience();
        relation.setDiaryId(diaryId);
        relation.setExperienceId(experienceId);
        diaryExperienceMapper.insert(relation);
    }

    private List<Long> ownedKnowledgeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Knowledge> found = knowledgeMapper.selectByIds(ids).stream()
                .filter(item -> Objects.equals(item.getUserId(), CurrentUser.id()))
                .collect(Collectors.toMap(Knowledge::getId, item -> item));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw BusinessException.badRequest("关联知识不存在");
            }
        }
        return new ArrayList<>(ids);
    }

    private Long createSuggestedKnowledge(Diary diary, Experience experience, KnowledgeDraftRequest draft, LocalDateTime now) {
        Knowledge knowledge = new Knowledge();
        knowledge.setUserId(CurrentUser.id());
        knowledge.setTitle(draft.getTitle().trim());
        knowledge.setCategory(StringUtils.hasText(draft.getCategory()) ? draft.getCategory().trim() : "未分类");
        knowledge.setDomain(normalizeDomain(StringUtils.hasText(draft.getDomain()) ? draft.getDomain() : experience.getDomain()));
        knowledge.setSummary(StringUtils.hasText(draft.getSummary())
                ? Texts.truncate(draft.getSummary(), 500)
                : Texts.truncate(experience.getLesson(), 60));
        knowledge.setContent(StringUtils.hasText(draft.getContent()) ? draft.getContent() : defaultKnowledgeContent(diary, experience));
        knowledge.setMastery(Masteries.PENDING);
        knowledge.setVisibility(PRIVATE);
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        knowledgeMapper.insert(knowledge);
        replaceKnowledgeTags(knowledge.getId(), draft.getTags() == null ? List.of() : draft.getTags());
        replaceRelated(knowledge.getId(), draft.getRelatedIds());
        return knowledge.getId();
    }

    private String defaultKnowledgeContent(Diary diary, Experience experience) {
        return String.join("\n\n",
                "## 问题\n\n" + experience.getProblem(),
                "## 原因\n\n" + experience.getCause(),
                "## 解决方案\n\n" + experience.getSolution(),
                "## 经验\n\n" + experience.getLesson(),
                "---\n\n由日记《" + diary.getTitle() + "》沉淀，待补充完善。");
    }

    private void replaceKnowledgeLinks(Long diaryId, Long experienceId, List<Long> knowledgeIds) {
        experienceKnowledgeMapper.delete(Wrappers.<ExperienceKnowledge>lambdaQuery()
                .eq(ExperienceKnowledge::getExperienceId, experienceId));
        Set<Long> unique = new LinkedHashSet<>(knowledgeIds);
        for (Long knowledgeId : unique) {
            ExperienceKnowledge experienceLink = new ExperienceKnowledge();
            experienceLink.setExperienceId(experienceId);
            experienceLink.setKnowledgeId(knowledgeId);
            experienceKnowledgeMapper.insert(experienceLink);
        }
        rebuildDiaryKnowledge(diaryId);
    }

    private void rebuildDiaryKnowledge(Long diaryId) {
        diaryKnowledgeMapper.delete(Wrappers.<DiaryKnowledge>lambdaQuery().eq(DiaryKnowledge::getDiaryId, diaryId));
        List<Experience> experiences = experienceMapper.selectList(
                Wrappers.<Experience>lambdaQuery().eq(Experience::getDiaryId, diaryId));
        if (experiences.isEmpty()) {
            return;
        }
        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();
        Set<Long> knowledgeIds = new LinkedHashSet<>();
        experienceKnowledgeMapper.selectList(Wrappers.<ExperienceKnowledge>lambdaQuery()
                        .in(ExperienceKnowledge::getExperienceId, experienceIds))
                .forEach(item -> knowledgeIds.add(item.getKnowledgeId()));
        for (Long knowledgeId : knowledgeIds) {
            DiaryKnowledge diaryLink = new DiaryKnowledge();
            diaryLink.setDiaryId(diaryId);
            diaryLink.setKnowledgeId(knowledgeId);
            diaryKnowledgeMapper.insert(diaryLink);
        }
    }

    private void replaceRelated(Long knowledgeId, List<Long> relatedIds) {
        knowledgeRelatedMapper.delete(Wrappers.<KnowledgeRelated>lambdaQuery()
                .eq(KnowledgeRelated::getKnowledgeId, knowledgeId));
        if (relatedIds == null) {
            return;
        }
        for (Long relatedId : relatedIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (Objects.equals(relatedId, knowledgeId)) {
                continue;
            }
            Knowledge related = knowledgeMapper.selectById(relatedId);
            if (related == null || !Objects.equals(related.getUserId(), CurrentUser.id())) {
                continue;
            }
            KnowledgeRelated row = new KnowledgeRelated();
            row.setKnowledgeId(knowledgeId);
            row.setRelatedId(relatedId);
            knowledgeRelatedMapper.insert(row);
        }
    }

    private List<String> replaceTags(Long experienceId, List<String> rawTags) {
        List<String> names = Texts.normalizeTags(rawTags);
        experienceTagMapper.delete(Wrappers.<ExperienceTag>lambdaQuery().eq(ExperienceTag::getExperienceId, experienceId));
        for (String name : names) {
            ExperienceTag relation = new ExperienceTag();
            relation.setExperienceId(experienceId);
            relation.setTagId(findOrCreateTag(name).getId());
            experienceTagMapper.insert(relation);
        }
        return names;
    }

    private void replaceKnowledgeTags(Long knowledgeId, List<String> rawTags) {
        List<String> names = Texts.normalizeTags(rawTags);
        knowledgeTagMapper.delete(Wrappers.<KnowledgeTag>lambdaQuery().eq(KnowledgeTag::getKnowledgeId, knowledgeId));
        for (String name : names) {
            KnowledgeTag relation = new KnowledgeTag();
            relation.setKnowledgeId(knowledgeId);
            relation.setTagId(findOrCreateTag(name).getId());
            knowledgeTagMapper.insert(relation);
        }
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

    private Map<Long, List<String>> loadTagNames(List<Long> experienceIds) {
        if (experienceIds.isEmpty()) {
            return Map.of();
        }
        List<ExperienceTag> relations = experienceTagMapper.selectList(
                Wrappers.<ExperienceTag>lambdaQuery().in(ExperienceTag::getExperienceId, experienceIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameById = tagMapper.selectByIds(relations.stream().map(ExperienceTag::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (ExperienceTag relation : relations) {
            String name = nameById.get(relation.getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getExperienceId(), key -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private Map<Long, List<Long>> loadKnowledgeIds(List<Long> experienceIds) {
        if (experienceIds.isEmpty()) {
            return Map.of();
        }
        List<ExperienceKnowledge> relations = experienceKnowledgeMapper.selectList(
                Wrappers.<ExperienceKnowledge>lambdaQuery().in(ExperienceKnowledge::getExperienceId, experienceIds));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (ExperienceKnowledge relation : relations) {
            result.computeIfAbsent(relation.getExperienceId(), key -> new ArrayList<>()).add(relation.getKnowledgeId());
        }
        return result;
    }

    private void normalizeQuery(ExperienceQuery query) {
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

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return DEFAULT_DOMAIN;
        }
        return domain.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private ExperienceDetailVO toDetail(Experience experience, String diaryTitle, List<String> tags, List<Long> knowledgeIds) {
        ExperienceDetailVO vo = new ExperienceDetailVO();
        vo.setId(experience.getId());
        vo.setTitle(experience.getTitle());
        vo.setProblem(experience.getProblem());
        vo.setCause(experience.getCause());
        vo.setSolution(experience.getSolution());
        vo.setLesson(experience.getLesson());
        vo.setTags(tags);
        vo.setDomain(experience.getDomain());
        vo.setDiaryId(experience.getDiaryId());
        vo.setDiaryTitle(diaryTitle);
        vo.setKnowledgeIds(knowledgeIds);
        vo.setVisibility(PRIVATE);
        vo.setCreatedAt(experience.getCreatedAt());
        vo.setUpdatedAt(experience.getUpdatedAt());
        return vo;
    }

    private ExperienceListItemVO toListItem(Experience experience, List<String> tags, List<Long> knowledgeIds) {
        ExperienceListItemVO vo = new ExperienceListItemVO();
        vo.setId(experience.getId());
        vo.setTitle(experience.getTitle());
        vo.setProblem(experience.getProblem());
        vo.setCause(experience.getCause());
        vo.setSolution(experience.getSolution());
        vo.setLesson(experience.getLesson());
        vo.setTags(tags);
        vo.setDomain(experience.getDomain());
        vo.setDiaryId(experience.getDiaryId());
        vo.setKnowledgeIds(knowledgeIds);
        vo.setVisibility(PRIVATE);
        vo.setCreatedAt(experience.getCreatedAt());
        return vo;
    }
}
