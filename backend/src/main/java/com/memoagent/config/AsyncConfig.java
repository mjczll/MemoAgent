package com.memoagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 后台任务线程池。
 * <p>
 * 目前只有「日记保存后自动提取经验候选」在用。刻意把核心线程设为 1：
 * 本地 Ollama 一次只能跑一个模型，并发提交只会让请求互相排队甚至把内存打满；
 * 队列满时用 CallerRunsPolicy 退回调用线程，宁可慢一点也不要静默丢任务。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("diaryAiExecutor")
    public Executor diaryAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("diary-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 向量写入线程池。和上面分开，避免「提取经验候选」这种长任务堵住向量写入，
     * 否则保存后要等几十秒才能搜到。
     * <p>
     * 同样刻意串行（core = max = 1）：同一个资产短时间内被改两次时，
     * 串行能保证后一次读到的是最终内容；并发则可能出现「先读旧值、后写旧值」把新向量盖回去。
     * 队列给 200，采访整理一次写十几条也放得下；满了退回调用线程，不丢任务。
     */
    @Bean("vectorExecutor")
    public Executor vectorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("vector-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 会话标题生成。单独一个池，避免被「提取经验候选」那种十几秒的任务挡住——
     * 标题生成晚一两秒可接受，但排在长任务后面就变成几十秒了。
     * 丢任务比慢更糟，所以队列满时同样退回调用线程。
     */
    @Bean("titleExecutor")
    public Executor titleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("conv-title-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
