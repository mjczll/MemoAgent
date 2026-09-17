CREATE TABLE IF NOT EXISTS diary_kind (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diary_kind_user (user_id, deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL DEFAULT 1,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    diary_date DATE NOT NULL,
    kind VARCHAR(32) NOT NULL,
    kind_id BIGINT DEFAULT NULL,
    origin VARCHAR(32) NOT NULL DEFAULT 'manual',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_diary_user_date (user_id, diary_date, deleted),
    KEY idx_diary_kind (user_id, kind_id, deleted)
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

INSERT INTO diary_kind (user_id, name, sort_order, is_default, deleted, created_at, updated_at)
VALUES
    (1, '日常', 0, 0, 0, NOW(), NOW()),
    (1, '技术', 1, 1, 0, NOW(), NOW()),
    (1, '项目', 2, 0, 0, NOW(), NOW()),
    (1, '学习', 3, 0, 0, NOW(), NOW()),
    (1, '问题', 4, 0, 0, NOW(), NOW()),
    (1, '思考', 5, 0, 0, NOW(), NOW()),
    (1, '复盘', 6, 0, 0, NOW(), NOW());
