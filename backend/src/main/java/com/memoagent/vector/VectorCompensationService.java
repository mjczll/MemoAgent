package com.memoagent.vector;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.config.VectorProperties;
import com.memoagent.entity.Diary;
import com.memoagent.entity.Experience;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.VectorTask;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 向量写入的自动补偿。
 * <p>
 * 保存时向量写失败（模型不可用、pgvector 连不上、维度不符）不会影响业务落库，
 * 失败的那一条会登记进 vector_task。这个服务做两件事：
 * <p>
 * 1. 定时扫描到期任务重试，按 1 / 2 / 5 / 15 / 30 分钟退避，超过上限标记 abandoned 不再打扰；
 * 2. 启动时自检一遍，把「MySQL 有、向量库里没有」的资产补进队列，
 *    同时清掉「MySQL 已删、向量还在」的残留。
 * <p>
 * 都是同步、串行执行。阶段 7 的异步 worker 落地后，这里可以整体替换成消费者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorCompensationService {

    /** 退避梯度（分钟）。数组长度用尽后一直取最后一档。 */
    private static final int[] BACKOFF_MINUTES = {1, 2, 5, 15, 30};

    /** 启动自检最多扫这么多条资产，避免数据量大时拖慢启动。 */
    private static final int SELF_CHECK_LIMIT = 2000;

    private final VectorTaskQueue taskQueue;
    private final VectorIndexService vectorIndexService;
    private final VectorProperties properties;
    private final ObjectProvider<PgVectorRepository> repositoryProvider;
    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;

    /**
     * 定时重试。fixedDelay 保证上一轮跑完才排下一轮，不会堆积。
     * 间隔可用 memoagent.vector.retry.interval-ms 调整。
     */
    @Scheduled(fixedDelayString = "${memoagent.vector.retry.interval-ms:60000}",
            initialDelayString = "${memoagent.vector.retry.initial-delay-ms:30000}")
    public void retryPending() {
        if (!properties.getRetry().isEnabled()) {
            return;
        }
        List<VectorTask> tasks;
        try {
            tasks = taskQueue.due(properties.getRetry().getBatchSize());
        } catch (RuntimeException exception) {
            // 表还没建（db/vector-task.sql 没执行）时不要每小时刷一大段堆栈
            log.warn("读取向量补偿任务失败，本轮跳过：{}", exception.getMessage());
            return;
        }
        if (tasks.isEmpty()) {
            return;
        }
        int succeeded = 0;
        int failed = 0;
        int abandoned = 0;
        for (VectorTask task : tasks) {
            try {
                VectorIndexService.IndexResult result =
                        vectorIndexService.attempt(task.getSourceType(), task.getSourceId());
                if (result == null) {
                    // 向量库被关掉了，剩下的任务留着，等重新开启再处理
                    log.debug("向量库未启用，补偿暂停");
                    break;
                }
                if (result.success()) {
                    taskQueue.clear(task.getSourceType(), task.getSourceId());
                    succeeded += 1;
                    continue;
                }
                failed += 1;
                if (reschedule(task, result.error())) {
                    abandoned += 1;
                }
            } catch (RuntimeException exception) {
                // 单条任务出问题不该拖垮整轮，下一轮还会再来一次
                log.warn("处理向量补偿任务失败：{} #{}", task.getSourceType(), task.getSourceId(), exception);
            }
        }
        log.info("向量补偿：本轮 {} 条，成功 {} 条，失败 {} 条，放弃 {} 条",
                tasks.size(), succeeded, failed, abandoned);
    }

    /**
     * 启动自检：比对 MySQL 与向量库，把缺的登记进队列、多的清掉。
     * 只做比对和入队，不在这里调 embedding，真正的写入交给定时任务，避免拖慢启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void selfCheckOnStartup() {
        if (!properties.getRetry().isEnabled() || !properties.getRetry().isSelfCheck()) {
            return;
        }
        PgVectorRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        long userId = CurrentUser.id();
        try {
            Set<VectorRef> indexed = new HashSet<>(repository.listRefs(userId));
            Set<VectorRef> alive = new HashSet<>();
            List<Long> diaries = diaryIds();
            List<Long> experiences = experienceIds();
            List<Long> knowledges = knowledgeIds();
            int queued = 0;
            queued += queueMissing(indexed, alive, VectorSourceTypes.DIARY,
                    diaries, id -> new VectorRef(VectorSourceTypes.DIARY, id));
            queued += queueMissing(indexed, alive, VectorSourceTypes.EXPERIENCE,
                    experiences, id -> new VectorRef(VectorSourceTypes.EXPERIENCE, id));
            queued += queueMissing(indexed, alive, VectorSourceTypes.KNOWLEDGE,
                    knowledges, id -> new VectorRef(VectorSourceTypes.KNOWLEDGE, id));
            // 扫描被 limit 截断时 alive 是不完整的，此时清残留会把没扫到的资产向量误删
            boolean scannedAll = diaries.size() < SELF_CHECK_LIMIT
                    && experiences.size() < SELF_CHECK_LIMIT
                    && knowledges.size() < SELF_CHECK_LIMIT;
            int removed = 0;
            if (scannedAll) {
                removed = removeOrphans(repository, indexed, alive);
            } else {
                log.info("向量自检：资产数量达到扫描上限 {}，本次跳过残留向量清理，避免误删", SELF_CHECK_LIMIT);
            }
            if (queued > 0 || removed > 0) {
                log.info("向量自检：登记 {} 条待补偿，清理 {} 条残留向量", queued, removed);
            }
        } catch (RuntimeException exception) {
            log.warn("向量自检失败，本轮跳过：{}", exception.getMessage());
        }
    }

    /**
     * 缺向量的资产入队。返回入队条数。
     * alive 顺手把「MySQL 里真实存在」的定位收集起来，供清理残留用。
     */
    private int queueMissing(Set<VectorRef> indexed,
                             Set<VectorRef> alive,
                             String sourceType,
                             List<Long> ids,
                             Function<Long, VectorRef> refOf) {
        int queued = 0;
        for (Long id : ids) {
            VectorRef ref = refOf.apply(id);
            alive.add(ref);
            if (indexed.contains(ref)) {
                continue;
            }
            taskQueue.schedule(sourceType, id, "启动自检：向量库中缺少该资产");
            queued += 1;
        }
        return queued;
    }

    private int removeOrphans(PgVectorRepository repository, Set<VectorRef> indexed, Set<VectorRef> alive) {
        int removed = 0;
        for (VectorRef ref : indexed) {
            if (alive.contains(ref)) {
                continue;
            }
            try {
                repository.delete(ref.sourceType(), ref.sourceId());
                removed += 1;
            } catch (RuntimeException exception) {
                log.warn("清理残留向量失败：{} #{}", ref.sourceType(), ref.sourceId(), exception);
            }
        }
        return removed;
    }

    /** 返回 true 表示已放弃，不再重试。 */
    private boolean reschedule(VectorTask task, String error) {
        int attempts = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        String type = task.getSourceType();
        long id = task.getSourceId();
        if (attempts >= properties.getRetry().getMaxAttempts()) {
            taskQueue.abandon(task, error);
            log.warn("向量补偿放弃：{} #{}，已重试 {} 次。请检查 embedding 配置后重新保存或手动补量", type, id, attempts);
            return true;
        }
        int minutes = BACKOFF_MINUTES[Math.min(attempts - 1, BACKOFF_MINUTES.length - 1)];
        taskQueue.reschedule(task, attempts, minutes, error);
        log.info("向量补偿失败，{} 分钟后重试（第 {} 次）：{} #{}", minutes, attempts, type, id);
        return false;
    }

    /** 只取 id，避免把整篇正文拉进内存。 */
    private List<Long> diaryIds() {
        return diaryMapper.selectList(Wrappers.<Diary>lambdaQuery()
                        .select(Diary::getId)
                        .eq(Diary::getUserId, CurrentUser.id())
                        .last("limit " + SELF_CHECK_LIMIT))
                .stream().map(Diary::getId).toList();
    }

    private List<Long> experienceIds() {
        return experienceMapper.selectList(Wrappers.<Experience>lambdaQuery()
                        .select(Experience::getId)
                        .eq(Experience::getUserId, CurrentUser.id())
                        .last("limit " + SELF_CHECK_LIMIT))
                .stream().map(Experience::getId).toList();
    }

    private List<Long> knowledgeIds() {
        return knowledgeMapper.selectList(Wrappers.<Knowledge>lambdaQuery()
                        .select(Knowledge::getId)
                        .eq(Knowledge::getUserId, CurrentUser.id())
                        .last("limit " + SELF_CHECK_LIMIT))
                .stream().map(Knowledge::getId).toList();
    }
}
