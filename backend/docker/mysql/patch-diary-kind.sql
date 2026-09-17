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

SET @kind_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'diary'
      AND COLUMN_NAME = 'kind_id'
);
SET @add_kind_id := IF(
    @kind_id_exists = 0,
    'ALTER TABLE diary ADD COLUMN kind_id BIGINT DEFAULT NULL AFTER kind, ADD KEY idx_diary_kind (user_id, kind_id, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @add_kind_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO diary_kind (user_id, name, sort_order, is_default, deleted, created_at, updated_at)
SELECT seed.user_id, seed.name, seed.sort_order, seed.is_default, 0, NOW(), NOW()
FROM (
    SELECT 1 AS user_id, '日常' AS name, 0 AS sort_order, 0 AS is_default
    UNION ALL SELECT 1, '技术', 1, 1
    UNION ALL SELECT 1, '项目', 2, 0
    UNION ALL SELECT 1, '学习', 3, 0
    UNION ALL SELECT 1, '问题', 4, 0
    UNION ALL SELECT 1, '思考', 5, 0
    UNION ALL SELECT 1, '复盘', 6, 0
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM diary_kind existing
    WHERE existing.user_id = seed.user_id
      AND existing.name = seed.name
      AND existing.deleted = 0
);

UPDATE diary d
JOIN diary_kind k ON k.user_id = d.user_id AND k.name = d.kind AND k.deleted = 0
SET d.kind_id = k.id
WHERE d.kind_id IS NULL;
