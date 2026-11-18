-- =============================================================================
-- MySQL 初始化脚本。docker/docker-compose.yml 把它挂到容器的
-- /docker-entrypoint-initdb.d/ 下，**只在数据卷为空时自动执行一次**。
--
-- 注意：已有的库不会因为改了这个文件而更新。给线上/本地已有环境加表或改字段，
-- 必须单独写脚本手动执行，增量脚本统一放 docs/sql/（按日期命名）。
--
-- 本文件是「全新环境一次建好」的唯一来源，必须与 backend 的 entity 包保持一致。
-- 当前包含 20 张表：
--   内容资产  diary / experience / knowledge / diary_candidate
--   标签关系  tag / diary_tag / experience_tag / knowledge_tag
--   资产关联  diary_experience / experience_knowledge / diary_knowledge / knowledge_related
--   对话      conversation / conversation_message
--   异步任务  vector_task
--   个人偏好  profile
--   Obsidian  obsidian_plugin / obsidian_import_task /
--             obsidian_import_task_file / obsidian_sync_state
-- PostgreSQL 侧的 vector_asset 不在这里，见 db/vector-init.sql。
-- =============================================================================

CREATE TABLE IF NOT EXISTS diary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    diary_date DATE NOT NULL,
    origin VARCHAR(32) NOT NULL DEFAULT 'manual',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diary_user_date (user_id, diary_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'custom',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_tag (diary_id, tag_id),
    KEY idx_diary_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS experience (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    diary_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    problem TEXT NOT NULL,
    cause TEXT,
    solution TEXT,
    lesson TEXT,
    domain VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'private',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_exp_user (user_id, deleted),
    KEY idx_exp_diary (diary_id, deleted),
    KEY idx_exp_domain (user_id, domain, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(64) DEFAULT NULL,
    domain VARCHAR(64) NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    content MEDIUMTEXT NOT NULL,
    mastery VARCHAR(32) NOT NULL DEFAULT '待补充',
    visibility VARCHAR(16) NOT NULL DEFAULT 'private',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_know_user (user_id, deleted),
    KEY idx_know_domain (user_id, domain, deleted),
    KEY idx_know_mastery (user_id, mastery, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS experience_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    experience_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exp_tag (experience_id, tag_id),
    KEY idx_exp_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_know_tag (knowledge_id, tag_id),
    KEY idx_know_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary_experience (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    experience_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_exp (diary_id, experience_id),
    KEY idx_de_exp (experience_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS experience_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    experience_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exp_know (experience_id, knowledge_id),
    KEY idx_ek_know (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    knowledge_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_know (diary_id, knowledge_id),
    KEY idx_dk_know (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    pinned TINYINT NOT NULL DEFAULT 0,
    draft_json MEDIUMTEXT,
    result_json TEXT,
    committed_diary_id BIGINT DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_conversation_user (user_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    text MEDIUMTEXT NOT NULL,
    payload_json MEDIUMTEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_conversation_message (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_related (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    related_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_know_rel (knowledge_id, related_id),
    KEY idx_kr_related (related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vector_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    last_error VARCHAR(500) DEFAULT NULL,
    next_retry_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vector_task_source (source_type, source_id),
    KEY idx_vector_task_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量写入补偿任务';

CREATE TABLE IF NOT EXISTS diary_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    diary_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    problem TEXT NOT NULL,
    cause TEXT,
    solution TEXT,
    lesson TEXT,
    domain VARCHAR(64) NOT NULL,
    tags_json VARCHAR(1000) DEFAULT NULL,
    knowledge_json MEDIUMTEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cand_diary (diary_id, status),
    KEY idx_cand_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记经验候选';

-- ---------------------------------------------------------------------------
-- 个人偏好（阶段 8.2）：单用户，user_id 即主键。
-- 与 entity/Profile 一一对应；后端首次 GET /api/profile 时自动写入默认行。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS profile (
    user_id      BIGINT       NOT NULL COMMENT '用户 id（当前单用户固定 1）',
    display_name VARCHAR(64)  NOT NULL DEFAULT '未命名' COMMENT '昵称',
    signature    VARCHAR(200) NOT NULL DEFAULT '' COMMENT '一句话签名',
    review_day   TINYINT      NOT NULL DEFAULT 0 COMMENT '每周回顾触发日：0=周日 1=周一 ... 6=周六',
    review_time  VARCHAR(8)   NOT NULL DEFAULT '21:30' COMMENT '每周回顾触发时间 HH:mm',
    auto_extract TINYINT      NOT NULL DEFAULT 1 COMMENT '采访整理后自动结构化提炼（问题/原因/方案/经验）',
    weekly_review TINYINT     NOT NULL DEFAULT 1 COMMENT '每周生成一次回顾（到点进待办，不自动生成）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人偏好（阶段 8.2）';

-- ---------------------------------------------------------------------------
-- Obsidian 插件式单向导入（4 张表）
-- 详情见 docs/sql/2026-11-18_obsidian_plugin_import.sql
-- ---------------------------------------------------------------------------

-- 插件安装实例（一个 Obsidian 安装 = 一行；配对后长期有效，可被撤销）
CREATE TABLE IF NOT EXISTS obsidian_plugin (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL DEFAULT 1 COMMENT '用户 id（当前单用户固定 1）',
    installation_id VARCHAR(64)  NOT NULL COMMENT '插件生成的安装标识，卸载重装会变',
    vault_name      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '绑定的 Obsidian Vault 名称（目录名）',
    token_hash      CHAR(64)     NOT NULL COMMENT '插件 token 的 SHA-256（不存明文）',
    token_hint      VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'token 首尾各 4 位，仅用于排查是哪一个凭证',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  DATETIME     NULL COMMENT '最后一次带 token 的请求时间',
    revoked         TINYINT      NOT NULL DEFAULT 0 COMMENT '1=已撤销，需重新配对',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plugin_installation (installation_id),
    KEY idx_plugin_user (user_id, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Obsidian 插件安装实例与 Vault 绑定';

-- 导入任务
-- status: PENDING / RUNNING / COMPLETED / PARTIAL_FAILED / FAILED / EXPIRED / SUPERSEDED
CREATE TABLE IF NOT EXISTS obsidian_import_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_id         CHAR(32)     NOT NULL COMMENT '对外暴露的任务号（UUID 去横线），不可预测',
    user_id         BIGINT       NOT NULL DEFAULT 1,
    installation_id VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建时绑定的插件实例',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    total           INT          NOT NULL DEFAULT 0 COMMENT '任务内文件总数',
    processed       INT          NOT NULL DEFAULT 0 COMMENT '插件已处理数（含跳过与失败）',
    success_count   INT          NOT NULL DEFAULT 0,
    skipped_count   INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    conflict_count  INT          NOT NULL DEFAULT 0 COMMENT '本地与远端都改过的文件数',
    message         VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败摘要，展示用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at      DATETIME     NOT NULL COMMENT '过期后插件不再领取，前端提示重新导入',
    started_at      DATETIME     NULL COMMENT '插件首次领取时间',
    completed_at    DATETIME     NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_task_id (task_id),
    KEY idx_task_user_status (user_id, status),
    KEY idx_task_install_status (installation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Obsidian 导入任务';

-- 任务内文件
-- op_type: CREATE / UPDATE / DELETE    status: PENDING / SUCCESS / SKIPPED / FAILED / CONFLICT
-- content 为任务生成那一刻的快照，执行期间资产再被编辑也不会写到不一致的正文
CREATE TABLE IF NOT EXISTS obsidian_import_task_file (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    task_id          CHAR(32)     NOT NULL,
    path             VARCHAR(512) NOT NULL COMMENT '相对 Vault 根，固定以 MemoAgent/ 开头',
    content          MEDIUMTEXT   NULL COMMENT 'Markdown 全文；DELETE 类为 NULL',
    hash             CHAR(16)     NOT NULL DEFAULT '' COMMENT '内容 SHA-256 前 16 位，与 /notes 接口一致',
    op_type          VARCHAR(8)   NOT NULL COMMENT 'CREATE / UPDATE / DELETE',
    asset_type       VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'readme / diary / experience / knowledge',
    asset_id         BIGINT       NULL COMMENT '对应资产主键，便于将来追踪与双向同步',
    last_synced_hash CHAR(16)     NOT NULL DEFAULT '' COMMENT '任务生成时本地已知的同步版本，空=从未同步',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error            VARCHAR(500) NOT NULL DEFAULT '',
    processed_at     DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_task_file_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Obsidian 导入任务内的文件';

-- 同步快照（每个插件实例 × 每个 path 一行），DELETE 与冲突判定的唯一依据
CREATE TABLE IF NOT EXISTS obsidian_sync_state (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL DEFAULT 1,
    installation_id  VARCHAR(64)  NOT NULL,
    path             VARCHAR(512) NOT NULL,
    path_hash        CHAR(64)     NOT NULL COMMENT 'path 的 SHA-256，用来做唯一键（path 过长无法直接建索引）',
    asset_type       VARCHAR(16)  NOT NULL DEFAULT '',
    asset_id         BIGINT       NULL,
    last_synced_hash CHAR(16)     NOT NULL DEFAULT '' COMMENT '上次成功写入的正文 hash',
    last_synced_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_install_path (installation_id, path_hash),
    KEY idx_sync_asset (user_id, asset_type, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Obsidian 同步快照（用于删除同步与冲突判定）';
