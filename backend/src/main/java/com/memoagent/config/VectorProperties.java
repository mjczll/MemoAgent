package com.memoagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量库配置。只有 memoagent.vector.enabled=true 时才会建立连接，
 * 没起 pgvector 容器时把它设成 false 就不影响应用启动。
 * 当前默认值由 application.yml 决定（默认开启）。
 */
@Data
@ConfigurationProperties(prefix = "memoagent.vector")
public class VectorProperties {

    /** 是否启用向量库。关闭时不会创建任何连接，检索相关 Bean 也不装配。 */
    private boolean enabled;

    /**
     * PostgreSQL 地址。端口对外是 5433（容器内 5432），
     * 和 MySQL 用 3307 是同一个理由：避开本机可能已占用的默认端口。
     */
    private String url = "jdbc:postgresql://localhost:5433/memoagent_vector";

    private String username = "memoagent";

    private String password = "memoagent";

    /**
     * 向量表名。表结构见 db/vector-init.sql，不是 Spring AI 默认的 vector_store。
     * 只允许字母数字下划线，避免配置写错时拼进 SQL 出问题。
     */
    private String table = "vector_asset";

    /**
     * 向量维度，必须和建表时的 vector(n) 一致，也必须和 embedding 模型的输出一致。
     * 1024 对应 bge-m3 与 mxbai-embed-large；换成 nomic-embed-text 要改成 768 并重建表。
     */
    private int dimensions = 1024;

    /** 连接池上限。向量检索是低频操作，不需要大的池。 */
    private int poolSize = 4;

    /** 写入失败后的自动补偿。关掉就回到「需要手动跑 reindex」的老行为。 */
    private Retry retry = new Retry();

    @Data
    public static class Retry {

        /** 总开关。关闭后不注册调度、不重试、也不做启动自检。 */
        private boolean enabled = true;

        /**
         * 启动时比对 MySQL 与向量库，把缺失的资产登记进补偿队列。
         * 只入队不 embed，成本是两次查询；数据量特别大时可以关掉，改用 reindex 手动补。
         */
        private boolean selfCheck = true;

        /** 每轮最多处理多少条到期任务。 */
        private int batchSize = 20;

        /** 单条最多重试几次，超过后标记 abandoned 不再自动重试。 */
        private int maxAttempts = 5;

        /** 两轮之间的间隔（毫秒），fixedDelay 语义：上一轮跑完才开始计时。 */
        private long intervalMs = 60_000;

        /** 应用启动后延迟多久开始第一轮，避开启动阶段的资源竞争。 */
        private long initialDelayMs = 30_000;
    }
}
