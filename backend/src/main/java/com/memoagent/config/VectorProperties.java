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
}
