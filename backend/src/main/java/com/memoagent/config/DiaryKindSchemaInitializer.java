package com.memoagent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DiaryKindSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        Integer kindIdExists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'diary'
                  AND COLUMN_NAME = 'kind_id'
                """,
                Integer.class);
        if (kindIdExists == null || kindIdExists == 0) {
            jdbcTemplate.execute("ALTER TABLE diary ADD COLUMN kind_id BIGINT DEFAULT NULL AFTER kind");
            log.info("Added diary.kind_id");
        }

        Integer indexExists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'diary'
                  AND INDEX_NAME = 'idx_diary_kind'
                """,
                Integer.class);
        if (indexExists == null || indexExists == 0) {
            jdbcTemplate.execute("ALTER TABLE diary ADD KEY idx_diary_kind (user_id, kind_id, deleted)");
        }
    }
}
