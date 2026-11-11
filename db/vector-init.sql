-- ============================================================================
-- 【操作类型】建库内对象（扩展 + 表 + 索引），不涉及 MySQL，无数据迁移
-- 【目的说明】阶段 6 的向量存储。pgvector 只存向量与回指 MySQL 的定位信息，
--             日记/经验/知识的正文、标签、关联关系仍全部留在 MySQL，不做双写。
--             本文件由容器首次初始化时自动执行（数据卷为空时才会跑）。
-- 【影响范围】仅 memoagent_vector 库（PostgreSQL 5433）。
--             MySQL 的 diary / experience / knowledge 等表不受任何影响。
-- 【执行顺序】1. docker compose up -d pgvector（首次启动自动执行本文件）
--             2. 确认扩展与表已创建（见文件末尾的校验语句）
--             3. 之后再开发向量写入与检索接口
--             如果数据卷已存在、本文件被修改过，需要手动 psql 执行，
--             或 docker compose down -v 后重建（后者会清空向量数据）。
-- 【回滚方案】DROP TABLE IF EXISTS vector_asset;
--             DROP EXTENSION IF EXISTS vector;
--             或直接 docker compose down -v 删除卷，回到没有向量库的状态。
--             注意：回滚只影响向量索引，MySQL 主数据不受影响，
--             之后重新写入即可重建（向量可从正文重新 embed 出来）。
-- ============================================================================

-- 扩展必须先于任何 vector(n) 列定义
CREATE EXTENSION IF NOT EXISTS vector;

-- 一条资产一条向量。同一份资产重复处理时按 (source_type, source_id) 覆盖，
-- 不做版本堆积，避免检索时同一内容出现多条稀释排序结果。
CREATE TABLE IF NOT EXISTS vector_asset (
    id            BIGSERIAL    PRIMARY KEY,
    -- diary / experience / knowledge，与 MySQL 表名对应
    source_type   VARCHAR(16)  NOT NULL,
    -- 对应 MySQL 主键，不做外键（跨库无法约束），由应用侧保证一致
    source_id     BIGINT       NOT NULL,
    -- 多用户隔离，检索时作为 metadata filter 的第一道条件
    user_id       BIGINT       NOT NULL DEFAULT 1,
    -- 被 embed 的原文，留一份便于排查「为什么召回了这条」
    content       TEXT         NOT NULL,
    -- 1024 维对应 bge-m3。若换 embedding 模型，这里必须同步改，
    -- 且改维度只能重建表（pgvector 不支持直接 ALTER 维度）。
    -- nomic-embed-text 是 768，mxbai-embed-large 是 1024。
    embedding     vector(1024) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_vector_asset UNIQUE (source_type, source_id)
);

-- 余弦距离（<=>）。HNSW 建在空表上很快，不需要等数据灌完。
-- m / ef_construction 用默认值即可，几十万条以内够用。
CREATE INDEX IF NOT EXISTS idx_vector_asset_embedding
    ON vector_asset USING hnsw (embedding vector_cosine_ops);

-- 按用户 + 类型做过滤时避免全表扫
CREATE INDEX IF NOT EXISTS idx_vector_asset_owner
    ON vector_asset (user_id, source_type);

-- ---------------------------------------------------------------------------
-- 校验：容器起来后执行下面两条，预期各返回 1 行
--   SELECT extname FROM pg_extension WHERE extname = 'vector';
--   SELECT column_name, udt_name FROM information_schema.columns
--    WHERE table_name = 'vector_asset' AND column_name = 'embedding';
-- ---------------------------------------------------------------------------
