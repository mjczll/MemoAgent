package com.memoagent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.memoagent.common.CurrentUser;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.vector.PgVectorRepository;
import com.memoagent.vector.VectorIndexService;
import com.memoagent.vector.VectorRef;
import com.memoagent.vector.VectorSourceTypes;
import com.memoagent.vo.ReindexResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 存量资产补量。上线向量写入之前已经存在的日记 / 经验 / 知识没有向量，
 * 需要一个一次性入口把它们补上，顺带清掉「MySQL 已删除、向量表还留着」的残留。
 * <p>
 * 同步执行（阶段 7 才下沉到 worker），分页扫描避免一次把全表读进内存；
 * 单条 embed 失败只计数，不中断整批，重跑一次即可补齐（upsert 是幂等的）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorReindexService {

    private static final int PAGE_SIZE = 200;

    private final ObjectProvider<PgVectorRepository> repositoryProvider;
    private final VectorIndexService vectorIndexService;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    public ReindexResultVO reindex() {
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return ReindexResultVO.unavailable("向量库未启用（memoagent.vector.enabled=false）或应用未连接 pgvector");
        }
        ReindexResultVO result = new ReindexResultVO();
        result.setAvailable(true);
        Set<VectorRef> existing = new HashSet<>();
        long userId = CurrentUser.id();

        for (int page = 1; ; page += 1) {
            List<Diary> rows = diaryMapper.selectPage(new Page<>(page, PAGE_SIZE),
                    Wrappers.<Diary>lambdaQuery().eq(Diary::getUserId, userId)).getRecords();
            for (Diary row : rows) {
                result.setScanned(result.getScanned() + 1);
                existing.add(new VectorRef(VectorSourceTypes.DIARY, row.getId()));
                count(result, vectorIndexService.index(VectorSourceTypes.DIARY, row.getId()));
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
        }

        for (int page = 1; ; page += 1) {
            List<Experience> rows = experienceMapper.selectPage(new Page<>(page, PAGE_SIZE),
                    Wrappers.<Experience>lambdaQuery().eq(Experience::getUserId, userId)).getRecords();
            for (Experience row : rows) {
                result.setScanned(result.getScanned() + 1);
                existing.add(new VectorRef(VectorSourceTypes.EXPERIENCE, row.getId()));
                count(result, vectorIndexService.index(VectorSourceTypes.EXPERIENCE, row.getId()));
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
        }

        for (int page = 1; ; page += 1) {
            List<Knowledge> rows = knowledgeMapper.selectPage(new Page<>(page, PAGE_SIZE),
                    Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getUserId, userId)).getRecords();
            for (Knowledge row : rows) {
                result.setScanned(result.getScanned() + 1);
                existing.add(new VectorRef(VectorSourceTypes.KNOWLEDGE, row.getId()));
                count(result, vectorIndexService.index(VectorSourceTypes.KNOWLEDGE, row.getId()));
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
        }

        try {
            for (VectorRef ref : repository.listRefs(userId)) {
                if (!existing.contains(ref) && vectorIndexService.remove(ref.sourceType(), ref.sourceId())) {
                    result.setRemoved(result.getRemoved() + 1);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("清理残留向量失败", exception);
            result.setNotice("残留向量清理未完整执行：" + exception.getMessage());
        }
        log.info("向量补量完成：扫描 {} 条，写入 {} 条，清理残留 {} 条，失败 {} 条",
                result.getScanned(), result.getIndexed(), result.getRemoved(), result.getFailed());
        return result;
    }

    private void count(ReindexResultVO result, boolean success) {
        if (success) {
            result.setIndexed(result.getIndexed() + 1);
        } else {
            result.setFailed(result.getFailed() + 1);
        }
    }
}
