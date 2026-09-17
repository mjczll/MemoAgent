package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.dto.DiaryKindCreateRequest;
import com.memoagent.dto.DiaryKindReorderRequest;
import com.memoagent.dto.DiaryKindUpdateRequest;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryKind;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryKindMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.service.DiaryKindService;
import com.memoagent.vo.DiaryKindVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryKindServiceImpl implements DiaryKindService {

    static final List<String> DEFAULT_NAMES = List.of("日常", "技术", "项目", "学习", "问题", "思考", "复盘");
    private static final String DEFAULT_NAME = "技术";

    private final DiaryKindMapper diaryKindMapper;
    private final DiaryMapper diaryMapper;

    @Override
    public List<DiaryKindVO> list() {
        ensureSeeded();
        return toVoList(listEntities());
    }

    @Override
    @Transactional
    public DiaryKindVO create(DiaryKindCreateRequest request) {
        ensureSeeded();
        String name = normalizeName(request.getName());
        assertUniqueName(name, null);

        List<DiaryKind> existing = listEntities();
        int nextSort = existing.stream().mapToInt(DiaryKind::getSortOrder).max().orElse(-1) + 1;
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || existing.isEmpty();

        LocalDateTime now = LocalDateTime.now();
        DiaryKind kind = new DiaryKind();
        kind.setUserId(CurrentUser.id());
        kind.setName(name);
        kind.setSortOrder(nextSort);
        kind.setDefaultKind(makeDefault);
        kind.setCreatedAt(now);
        kind.setUpdatedAt(now);
        diaryKindMapper.insert(kind);

        if (makeDefault) {
            unsetOtherDefaults(kind.getId());
        }
        return toVo(kind, 0);
    }

    @Override
    @Transactional
    public DiaryKindVO update(Long id, DiaryKindUpdateRequest request) {
        ensureSeeded();
        DiaryKind kind = requireOwned(id);
        boolean changed = false;

        if (request.getName() != null) {
            String name = normalizeName(request.getName());
            if (!name.equals(kind.getName())) {
                assertUniqueName(name, id);
                kind.setName(name);
                syncDiaryKindLabel(id, name);
                changed = true;
            }
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(kind.getDefaultKind())) {
            kind.setDefaultKind(true);
            unsetOtherDefaults(id);
            changed = true;
        } else if (Boolean.FALSE.equals(request.getIsDefault()) && Boolean.TRUE.equals(kind.getDefaultKind())) {
            throw BusinessException.badRequest("至少保留一个默认类型");
        }

        if (!changed) {
            throw BusinessException.badRequest("没有需要修改的内容");
        }
        kind.setUpdatedAt(LocalDateTime.now());
        diaryKindMapper.updateById(kind);
        return toVo(kind, countDiaries(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ensureSeeded();
        List<DiaryKind> current = listEntities();
        if (current.size() <= 1) {
            throw BusinessException.badRequest("至少保留一个日记类型");
        }
        DiaryKind kind = requireOwned(id);
        if (Boolean.TRUE.equals(kind.getDefaultKind())) {
            throw BusinessException.badRequest("请先将其他类型设为默认后再删除");
        }
        if (countDiaries(id) > 0) {
            throw BusinessException.badRequest("该类型下还有日记，无法删除");
        }
        diaryKindMapper.deleteById(id);
    }

    @Override
    @Transactional
    public List<DiaryKindVO> reorder(DiaryKindReorderRequest request) {
        ensureSeeded();
        List<Long> ids = request.getIds();
        List<DiaryKind> current = listEntities();
        Set<Long> currentIds = current.stream().map(DiaryKind::getId).collect(Collectors.toSet());
        if (ids.size() != current.size() || !currentIds.equals(new HashSet<>(ids))) {
            throw BusinessException.badRequest("类型排序不完整");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            DiaryKind kind = current.stream()
                    .filter(item -> Objects.equals(item.getId(), id))
                    .findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("类型排序不完整"));
            kind.setSortOrder(i);
            kind.setUpdatedAt(now);
            diaryKindMapper.updateById(kind);
        }
        return toVoList(listEntities());
    }

    @Override
    public DiaryKind requireByName(String name) {
        ensureSeeded();
        String normalized = normalizeName(name);
        DiaryKind kind = findByName(normalized);
        if (kind == null) {
            throw BusinessException.badRequest("日记类型不存在");
        }
        return kind;
    }

    private synchronized void ensureSeeded() {
        Long count = diaryKindMapper.selectCount(Wrappers.<DiaryKind>lambdaQuery()
                .eq(DiaryKind::getUserId, CurrentUser.id()));
        if (count == null || count == 0) {
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < DEFAULT_NAMES.size(); i++) {
                String name = DEFAULT_NAMES.get(i);
                DiaryKind kind = new DiaryKind();
                kind.setUserId(CurrentUser.id());
                kind.setName(name);
                kind.setSortOrder(i);
                kind.setDefaultKind(DEFAULT_NAME.equals(name));
                kind.setCreatedAt(now);
                kind.setUpdatedAt(now);
                diaryKindMapper.insert(kind);
            }
        }
        backfillDiaryKindIds();
    }

    private void backfillDiaryKindIds() {
        Long missing = diaryMapper.selectCount(Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getUserId, CurrentUser.id())
                .isNull(Diary::getKindId));
        if (missing == null || missing == 0) {
            return;
        }
        for (DiaryKind kind : listEntities()) {
            diaryMapper.update(null, Wrappers.<Diary>lambdaUpdate()
                    .eq(Diary::getUserId, CurrentUser.id())
                    .eq(Diary::getKind, kind.getName())
                    .isNull(Diary::getKindId)
                    .set(Diary::getKindId, kind.getId()));
        }
    }

    private List<DiaryKind> listEntities() {
        return diaryKindMapper.selectList(Wrappers.<DiaryKind>lambdaQuery()
                .eq(DiaryKind::getUserId, CurrentUser.id())
                .orderByAsc(DiaryKind::getSortOrder)
                .orderByAsc(DiaryKind::getId));
    }

    private DiaryKind requireOwned(Long id) {
        DiaryKind kind = diaryKindMapper.selectById(id);
        if (kind == null || !Objects.equals(kind.getUserId(), CurrentUser.id())) {
            throw BusinessException.notFound("日记类型不存在");
        }
        return kind;
    }

    private DiaryKind findByName(String name) {
        return diaryKindMapper.selectOne(Wrappers.<DiaryKind>lambdaQuery()
                .eq(DiaryKind::getUserId, CurrentUser.id())
                .eq(DiaryKind::getName, name)
                .last("LIMIT 1"));
    }

    private void assertUniqueName(String name, Long excludeId) {
        DiaryKind existing = findByName(name);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw BusinessException.badRequest("该类型已存在");
        }
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw BusinessException.badRequest("类型名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 32) {
            throw BusinessException.badRequest("类型名称不能超过32字");
        }
        return trimmed;
    }

    private void unsetOtherDefaults(Long keepId) {
        diaryKindMapper.update(null, Wrappers.<DiaryKind>lambdaUpdate()
                .eq(DiaryKind::getUserId, CurrentUser.id())
                .ne(DiaryKind::getId, keepId)
                .set(DiaryKind::getDefaultKind, false));
    }

    private void syncDiaryKindLabel(Long kindId, String name) {
        diaryMapper.update(null, Wrappers.<Diary>lambdaUpdate()
                .eq(Diary::getUserId, CurrentUser.id())
                .eq(Diary::getKindId, kindId)
                .set(Diary::getKind, name));
    }

    private long countDiaries(Long kindId) {
        Long count = diaryMapper.selectCount(Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getUserId, CurrentUser.id())
                .eq(Diary::getKindId, kindId));
        return count == null ? 0 : count;
    }

    private List<DiaryKindVO> toVoList(List<DiaryKind> kinds) {
        Map<Long, Long> counts = kinds.stream()
                .collect(Collectors.toMap(DiaryKind::getId, item -> countDiaries(item.getId())));
        return kinds.stream()
                .map(kind -> toVo(kind, counts.getOrDefault(kind.getId(), 0L)))
                .toList();
    }

    private DiaryKindVO toVo(DiaryKind kind, long diaryCount) {
        DiaryKindVO vo = new DiaryKindVO();
        vo.setId(kind.getId());
        vo.setName(kind.getName());
        vo.setSortOrder(kind.getSortOrder());
        vo.setDefault(Boolean.TRUE.equals(kind.getDefaultKind()));
        vo.setDiaryCount(diaryCount);
        return vo;
    }
}
