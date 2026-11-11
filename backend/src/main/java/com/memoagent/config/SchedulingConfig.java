package com.memoagent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开 Spring 的定时任务能力。
 * <p>
 * 目前只有向量补偿在用（{@code VectorCompensationService#retryPending}）。
 * 主动挂条件是为了在 memoagent.vector.retry.enabled=false 时完全不注册调度器，
 * 本地跑单元测试或临时排障都不会有后台线程去碰数据库。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "memoagent.vector.retry.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
