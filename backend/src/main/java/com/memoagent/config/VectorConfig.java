package com.memoagent.config;

import com.memoagent.vector.PgVectorRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库装配。
 * <p>
 * 这里刻意不把 PostgreSQL 的 DataSource 声明成 DataSource 类型的 Bean：
 * Spring Boot 的 DataSourceAutoConfiguration 是 @ConditionalOnMissingBean(DataSource.class)，
 * 一旦容器里出现第二个 DataSource，MyBatis、事务管理器那条链路就会立刻退化成需要手工全配。
 * 所以连接池由 PgVectorRepository 自己持有，容器里只暴露 PgVectorRepository 一个 Bean，
 * 原有的 MySQL 单数据源自动装配完全不受影响。
 * <p>
 * 把 memoagent.vector.enabled 设为 false 时不建连接、不校验表，
 * pgvector 容器没起来也能正常启动应用（当前共用配置默认是开启的，见 application.yml）。
 */
@Configuration
@EnableConfigurationProperties(VectorProperties.class)
public class VectorConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "memoagent.vector.enabled", havingValue = "true")
    public PgVectorRepository pgVectorRepository(VectorProperties properties,
                                                 ObjectProvider<EmbeddingModel> embeddingModels) {
        // 没有 embedding 模型时也允许启动，写入和检索时才报错，避免因为模型没配导致整个应用起不来
        return new PgVectorRepository(properties, embeddingModels.getIfAvailable());
    }
}
