package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.memoagent.common.CurrentUser;
import com.memoagent.common.DiaryKinds;
import com.memoagent.common.DiaryOrigins;
import com.memoagent.common.Texts;
import com.memoagent.dto.DiaryCreateRequest;
import com.memoagent.dto.DiaryQuery;
import com.memoagent.dto.DiaryUpdateRequest;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryExperience;
import com.memoagent.entity.DiaryKnowledge;
import com.memoagent.entity.DiaryTag;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Tag;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryExperienceMapper;
import com.memoagent.mapper.DiaryKnowledgeMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.DiaryTagMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.TagMapper;
import com.memoagent.service.DiaryService;
import com.memoagent.vo.DiaryDetailVO;
import com.memoagent.vo.DiaryListItemVO;
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
public class DiaryServiceImpl implements DiaryService {

    private static final int SUMMARY_MAX = 60;
    private static final String PRIVATE = "private";

    private final DiaryMapper diaryMapper;
    private final TagMapper tagMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final DiaryExperienceMapper diaryExperienceMapper;
    private final DiaryKnowledgeMapper diaryKnowledgeMapper;
    private final ExperienceMapper experienceMapper;

    @Override
    @Transactional
    public DiaryDetailVO create(DiaryCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Diary diary = new Diary();
        diary.setUserId(CurrentUser.id());
        diary.setTitle(request.getTitle().trim());
        diary.setContent(request.getContent());
        diary.setSummary(resolveSummary(request.getSummary(), request.getContent()));
        diary.setDiaryDate(request.getDate());
        diary.setKind(DiaryKinds.normalize(request.getKind()));
        diary.setOrigin(DiaryOrigins.normalize(request.getOrigin()));
        diary.setCreatedAt(now);
        diary.setUpdatedAt(now);
        diaryMapper.insert(diary);

        List<String> tags = replaceTags(diary.getId(), request.getTags());
        return toDetail(diary, tags);
    }

    @Override
    public PageData<DiaryListItemVO> list(DiaryQuery query) {
        normalizeQuery(query);
        Page<Diary> page = new Page<>(query.getPage(), query.getSize());
        boolean asc = "asc".equalsIgnoreCase(query.getSort());
        LambdaQueryWrapper<Diary> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Diary::getUserId, CurrentUser.id());
        if (StringUtils.hasText(query.getKind()) && !"all".equalsIgnoreCase(query.getKind())) {
            wrapper.eq(Diary::getKind, query.getKind().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(Diary::getTitle, keyword)
                    .or().like(Diary::getSummary, keyword)
                    .or().like(Diary::getContent, keyword));
        }
        wrapper.orderBy(true, asc, Diary::getDiaryDate)
                .orderBy(true, asc, Diary::getCreatedAt)
                .orderBy(true, asc, Diary::getId);

        Page<Diary> result = diaryMapper.selectPage(page, wrapper);
        List<Long> ids = result.getRecords().stream().map(Diary::getId).toList();
        Map<Long, List<String>> tagsByDiary = loadTagNames(ids);

        List<DiaryListItemVO> records = result.getRecords().stream()
                .map(diary -> toListItem(diary, tagsByDiary.getOrDefault(diary.getId(), List.of())))
                .toList();
        return new PageData<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public DiaryDetailVO get(Long id) {
        Diary diary = requireOwned(id);
        return toDetail(diary, loadTagNames(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override
    @Transactional
    public DiaryDetailVO update(Long id, DiaryUpdateRequest request) {
        Diary diary = requireOwned(id);
        diary.setTitle(request.getTitle().trim());
        diary.setContent(request.getContent());
        diary.setSummary(resolveSummary(request.getSummary(), request.getContent()));
        diary.setDiaryDate(request.getDate());
        diary.setKind(DiaryKinds.normalize(request.getKind()));
        if (request.getOrigin() != null) {
            diary.setOrigin(DiaryOrigins.normalize(request.getOrigin()));
        }
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.updateById(diary);

        List<String> tags = replaceTags(id, request.getTags());
        return toDetail(diary, tags);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        List<Experience> experiences = experienceMapper.selectList(
                Wrappers.<Experience>lambdaQuery().eq(Experience::getDiaryId, id));
        for (Experience experience : experiences) {
            experienceMapper.deleteById(experience.getId());
        }
        diaryMapper.deleteById(id);
    }

    private Diary requireOwned(Long id) {
        Diary diary = diaryMapper.selectById(id);
        if (diary == null || !Objects.equals(diary.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("日记不存在");
        }
        return diary;
    }

    private void normalizeQuery(DiaryQuery query) {
        if (query.getPage() < 1) {
            query.setPage(1);
        }
        if (query.getSize() < 1) {
            query.setSize(20);
        }
        if (query.getSize() > 100) {
            query.setSize(100);
        }
        if (!"asc".equalsIgnoreCase(query.getSort()) && !"desc".equalsIgnoreCase(query.getSort())) {
            query.setSort("desc");
        }
    }

    private String resolveSummary(String summary, String content) {
        if (StringUtils.hasText(summary)) {
            return Texts.truncate(summary, 500);
        }
        return Texts.truncate(content, SUMMARY_MAX);
    }

    private List<String> replaceTags(Long diaryId, List<String> rawTags) {
        List<String> names = Texts.normalizeTags(rawTags);
        diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, diaryId));
        for (String name : names) {
            Tag tag = findOrCreateTag(name);
            DiaryTag relation = new DiaryTag();
            relation.setDiaryId(diaryId);
            relation.setTagId(tag.getId());
            diaryTagMapper.insert(relation);
        }
        return names;
    }

    private Tag findOrCreateTag(String name) {
        Tag existing = tagMapper.selectOne(
                Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name).last("LIMIT 1"));
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

    private Map<Long, List<String>> loadTagNames(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) {
            return Map.of();
        }
        List<DiaryTag> relations = diaryTagMapper.selectList(
                Wrappers.<DiaryTag>lambdaQuery().in(DiaryTag::getDiaryId, diaryIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> tagIds = relations.stream().map(DiaryTag::getTagId).distinct().toList();
        Map<Long, String> nameById = tagMapper.selectByIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (DiaryTag relation : relations) {
            String name = nameById.get(relation.getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getDiaryId(), key -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private DiaryDetailVO toDetail(Diary diary, List<String> tags) {
        List<Long> experienceIds = loadExperienceIds(List.of(diary.getId())).getOrDefault(diary.getId(), List.of());
        List<Long> knowledgeIds = loadKnowledgeIds(List.of(diary.getId())).getOrDefault(diary.getId(), List.of());
        DiaryDetailVO vo = new DiaryDetailVO();
        vo.setId(diary.getId());
        vo.setTitle(diary.getTitle());
        vo.setDate(diary.getDiaryDate());
        vo.setKind(diary.getKind());
        vo.setOrigin(diary.getOrigin());
        vo.setSummary(diary.getSummary());
        vo.setContent(diary.getContent());
        vo.setTags(tags);
        vo.setVisibility(PRIVATE);
        vo.setExperienceCount(experienceIds.size());
        vo.setKnowledgeCount(knowledgeIds.size());
        vo.setExperienceIds(experienceIds);
        vo.setKnowledgeIds(knowledgeIds);
        vo.setCreatedAt(diary.getCreatedAt());
        vo.setUpdatedAt(diary.getUpdatedAt());
        return vo;
    }

    private DiaryListItemVO toListItem(Diary diary, List<String> tags) {
        List<Long> experienceIds = loadExperienceIds(List.of(diary.getId())).getOrDefault(diary.getId(), List.of());
        List<Long> knowledgeIds = loadKnowledgeIds(List.of(diary.getId())).getOrDefault(diary.getId(), List.of());
        DiaryListItemVO vo = new DiaryListItemVO();
        vo.setId(diary.getId());
        vo.setTitle(diary.getTitle());
        vo.setDate(diary.getDiaryDate());
        vo.setKind(diary.getKind());
        vo.setOrigin(diary.getOrigin());
        vo.setSummary(diary.getSummary());
        vo.setTags(tags);
        vo.setVisibility(PRIVATE);
        vo.setExperienceCount(experienceIds.size());
        vo.setKnowledgeCount(knowledgeIds.size());
        vo.setCreatedAt(diary.getCreatedAt());
        return vo;
    }

    private Map<Long, List<Long>> loadExperienceIds(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) {
            return Map.of();
        }
        List<DiaryExperience> relations = diaryExperienceMapper.selectList(
                Wrappers.<DiaryExperience>lambdaQuery().in(DiaryExperience::getDiaryId, diaryIds));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (DiaryExperience relation : relations) {
            result.computeIfAbsent(relation.getDiaryId(), key -> new ArrayList<>()).add(relation.getExperienceId());
        }
        return result;
    }

    private Map<Long, List<Long>> loadKnowledgeIds(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) {
            return Map.of();
        }
        List<DiaryKnowledge> relations = diaryKnowledgeMapper.selectList(
                Wrappers.<DiaryKnowledge>lambdaQuery().in(DiaryKnowledge::getDiaryId, diaryIds));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (DiaryKnowledge relation : relations) {
            result.computeIfAbsent(relation.getDiaryId(), key -> new ArrayList<>()).add(relation.getKnowledgeId());
        }
        return result;
    }
}
