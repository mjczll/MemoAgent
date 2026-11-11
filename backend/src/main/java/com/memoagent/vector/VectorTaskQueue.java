package com.memoagent.vector;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.entity.VectorTask;
import com.memoagent.mapper.VectorTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补偿任务队列的读写。只碰 MySQL 的 vector_task，不做任何 embedding 调用。
 * <p>
 * 单独抽出来是为了断开依赖环：VectorIndexService 写失败要入队，
 * 补偿服务重试又要调 VectorIndexService，如果两者直接互相依赖会形成构造器循环。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorTaskQueue {

    public static final String PENDING = "pending";
    public static final String ABANDONED = "abandoned";

    private static final int ERROR_MAX = 500;

    /** 首次失败后多久重试。退避策略下第一次只等一分钟，多数抖动很快就能恢复。 */
    private static final int FIRST_DELAY_MINUTES = 1;

    private final VectorTaskMapper taskMapper;

    /**
     * 登记一条待补偿任务。已存在时不重置重试次数，只刷新错误信息，
     * 避免「每次保存失败都把退避打回原点」；之前已放弃的则重新给一轮机会。
     */
    public void schedule(String sourceType, long sourceId, String error) {
        LocalDateTime now = LocalDateTime.now();
        VectorTask existing = find(sourceType, sourceId);
        if (existing == null) {
            VectorTask task = new VectorTask();
            task.setSourceType(sourceType);
            task.setSourceId(sourceId);
            task.setAttempts(0);
            task.setStatus(PENDING);
            task.setLastError(trim(error));
            task.setNextRetryAt(now.plusMinutes(FIRST_DELAY_MINUTES));
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            log.info("向量补偿已登记：{} #{}，{} 分钟后重试", sourceType, sourceId, FIRST_DELAY_MINUTES);
            return;
        }
        if (ABANDONED.equals(existing.getStatus())) {
            existing.setStatus(PENDING);
            existing.setAttempts(0);
            existing.setNextRetryAt(now.plusMinutes(FIRST_DELAY_MINUTES));
        }
        existing.setLastError(trim(error));
        existing.setUpdatedAt(now);
        taskMapper.updateById(existing);
    }

    /** 写入成功后清掉这条任务，包括之前已被放弃的残留。 */
    public void clear(String sourceType, long sourceId) {
        taskMapper.delete(Wrappers.<VectorTask>lambdaQuery()
                .eq(VectorTask::getSourceType, sourceType)
                .eq(VectorTask::getSourceId, sourceId));
    }

    /** 取一批到期的待重试任务，按到期时间从早到晚。 */
    public List<VectorTask> due(int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 200);
        return taskMapper.selectList(Wrappers.<VectorTask>lambdaQuery()
                .eq(VectorTask::getStatus, PENDING)
                .le(VectorTask::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(VectorTask::getNextRetryAt)
                .last("limit " + size));
    }

    /** 本次仍失败，推进退避时间。 */
    public void reschedule(VectorTask task, int attempts, int delayMinutes, String error) {
        LocalDateTime now = LocalDateTime.now();
        task.setAttempts(attempts);
        task.setLastError(trim(error));
        task.setNextRetryAt(now.plusMinutes(delayMinutes));
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
    }

    /** 超过重试上限，标记放弃，保留行供排查；不会自动删除。 */
    public void abandon(VectorTask task, String error) {
        task.setStatus(ABANDONED);
        task.setLastError(trim(error));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private VectorTask find(String sourceType, long sourceId) {
        return taskMapper.selectOne(Wrappers.<VectorTask>lambdaQuery()
                .eq(VectorTask::getSourceType, sourceType)
                .eq(VectorTask::getSourceId, sourceId)
                .last("limit 1"));
    }

    private static String trim(String error) {
        if (error == null) {
            return null;
        }
        String value = error.strip();
        return value.length() <= ERROR_MAX ? value : value.substring(0, ERROR_MAX);
    }
}
