package com.memoagent.vector;

import com.memoagent.config.VectorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 向量库的读写。用 JdbcTemplate 直连 PostgreSQL，不走 Spring AI 的 VectorStore 抽象，
 * 原因有两个：
 * <p>
 * 1. PgVectorStore 要求固定的 vector_store 表（id uuid / content / metadata json / embedding），
 * 不支持 source_type、source_id、user_id 这样的独立列。而这三列正是「回指 MySQL」
 * 和「按用户隔离」要用的，塞进 metadata json 查询会更慢也更难排查。
 * 2. 它的自动装配会去拿容器里的主数据源（MySQL），本项目虽然绕开了自动装配，
 * 但自己持有连接池更直观：一个专用 DataSource，和 MySQL 的 MyBatis 链路完全隔离。
 * <p>
 * 这个类自己持有 HikariDataSource，实现了 AutoCloseable，由 Spring 在容器关闭时调用。
 */
@Slf4j
public class PgVectorRepository implements AutoCloseable {

    /** 允许写入的来源类型，与 MySQL 的表名对应，也用于拼 SQL 前做白名单校验。 */
    private static final Set<String> SOURCE_TYPES = VectorSourceTypes.ALL;

    private static final String TABLE_PATTERN = "[a-z_][a-z0-9_]*";

    private final HikariDataSource dataSource;

    private final JdbcTemplate jdbc;

    /** 没有配置模型时允许为空，此时调用写入/检索会给出明确提示，不影响应用启动。 */
    private final EmbeddingModel embeddingModel;

    private final int dimensions;

    private final String table;

    public PgVectorRepository(VectorProperties properties, EmbeddingModel embeddingModel) {
        String tableName = properties.getTable() == null ? "" : properties.getTable().trim().toLowerCase(Locale.ROOT);
        if (!tableName.matches(TABLE_PATTERN)) {
            throw new IllegalStateException("memoagent.vector.table 只能由小写字母、数字和下划线组成，当前值：" + properties.getTable());
        }
        this.table = tableName;
        this.dimensions = properties.getDimensions();
        this.embeddingModel = embeddingModel;

        HikariConfig config = new HikariConfig();
        config.setPoolName("pgvector-pool");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(Math.max(1, properties.getPoolSize()));
        // 连接不上时尽快失败，不要卡住启动流程
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(5000);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);

        verify();
    }

    /**
     * 启动时确认表存在。表不存在通常意味着 db/vector-init.sql 没被执行过，
     * 直接报错并给出提示，好过第一次检索时抛一个看不懂的 SQL 异常。
     */
    private void verify() {
        try {
            Long total = jdbc.queryForObject("select count(*) from " + table, Long.class);
            log.info("向量库已就绪：{} @ {}，表 {} 现有 {} 条", dimensions + " 维", dataSource.getJdbcUrl(), table, total);
        } catch (RuntimeException exception) {
            close();
            throw new IllegalStateException("向量表 " + table + " 不可用。请确认 pgvector 容器已启动，"
                    + "并且 db/vector-init.sql 已执行（首次创建数据卷时容器会自动执行）", exception);
        }
    }

    /**
     * 写入或覆盖一条向量。同一份资产重复整理时按 (source_type, source_id) 覆盖，
     * 不堆版本，避免同一内容出现多条稀释排序结果。
     */
    public void upsert(String sourceType, long sourceId, long userId, String content) {
        requireType(sourceType);
        if (content == null || content.isBlank()) {
            // 正文被清空的资产，留着旧向量只会召回不到东西却占位，直接删掉更干净
            delete(sourceType, sourceId);
            return;
        }
        PGobject vector = toPgVector(embed(content));
        String sql = "insert into " + table
                + " (source_type, source_id, user_id, content, embedding, updated_at)"
                + " values (?, ?, ?, ?, ?, now())"
                + " on conflict (source_type, source_id) do update set"
                + " content = excluded.content, embedding = excluded.embedding,"
                + " user_id = excluded.user_id, updated_at = now()";
        jdbc.update(sql, sourceType, sourceId, userId, content, vector);
    }

    /**
     * 按余弦相似度取前 topK 条。sourceTypes 传空表示不限制类型。
     * score 是相似度（1 - 余弦距离），越大越相近。
     */
    public List<VectorMatch> search(long userId, String queryText, Collection<String> sourceTypes, int topK) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }
        PGobject vector = toPgVector(embed(queryText));
        StringBuilder sql = new StringBuilder("select source_type, source_id, content, 1 - (embedding <=> ?) as score")
                .append(" from ").append(table)
                .append(" where user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(vector);
        args.add(userId);
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            List<String> types = new ArrayList<>();
            for (String type : sourceTypes) {
                requireType(type);
                types.add(type);
            }
            sql.append(" and source_type in (")
                    .append(String.join(", ", Collections.nCopies(types.size(), "?")))
                    .append(")");
            args.addAll(types);
        }
        sql.append(" order by embedding <=> ? limit ?");
        args.add(vector);
        args.add(topK);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new VectorMatch(
                rs.getString("source_type"),
                rs.getLong("source_id"),
                rs.getString("content"),
                rs.getDouble("score")), args.toArray());
    }

    /** 资产被删除时清理向量。跨库没有外键，只能由应用侧保证一致。 */
    public void delete(String sourceType, long sourceId) {
        requireType(sourceType);
        jdbc.update("delete from " + table + " where source_type = ? and source_id = ?", sourceType, sourceId);
    }

    public long count() {
        Long total = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return total == null ? 0 : total;
    }

    /** 列出该用户在向量表里的全部资产定位，供补量时清理已删除资产的残留向量。 */
    public List<VectorRef> listRefs(long userId) {
        return jdbc.query("select source_type, source_id from " + table + " where user_id = ?",
                (rs, rowNum) -> new VectorRef(rs.getString("source_type"), rs.getLong("source_id")), userId);
    }

    private float[] embed(String text) {
        if (embeddingModel == null) {
            throw new IllegalStateException("没有可用的 EmbeddingModel，向量写入与检索无法进行。"
                    + "请确认 memoagent.embedding.provider 对应的模型可用"
                    + "（当前默认 local / bge-m3，需先 ollama pull bge-m3）");
        }
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("embedding 返回了空向量");
        }
        if (vector.length != dimensions) {
            throw new IllegalStateException("embedding 维度 " + vector.length + " 与表定义的 " + dimensions
                    + " 不一致，请统一 memoagent.vector.dimensions、建表语句和模型三处");
        }
        return vector;
    }

    /** pgvector 的 JDBC 用法：以 PGobject 形式传参，类型写成 vector。 */
    private PGobject toPgVector(float[] values) {
        StringBuilder literal = new StringBuilder(values.length * 10 + 2).append('[');
        for (int i = 0; i < values.length; i += 1) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(values[i]);
        }
        literal.append(']');
        PGobject vector = new PGobject();
        vector.setType("vector");
        try {
            vector.setValue(literal.toString());
        } catch (SQLException exception) {
            throw new IllegalStateException("向量格式不合法", exception);
        }
        return vector;
    }

    private void requireType(String sourceType) {
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("未知的向量来源类型：" + sourceType + "，只支持 " + SOURCE_TYPES);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
