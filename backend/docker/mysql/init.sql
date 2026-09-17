CREATE TABLE IF NOT EXISTS diary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    diary_date DATE NOT NULL,
    kind VARCHAR(32) NOT NULL,
    origin VARCHAR(32) NOT NULL DEFAULT 'manual',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diary_user_date (user_id, diary_date, deleted),
    KEY idx_diary_user_kind (user_id, kind, deleted)
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

CREATE TABLE IF NOT EXISTS knowledge_related (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    related_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_know_rel (knowledge_id, related_id),
    KEY idx_kr_related (related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
