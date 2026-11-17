package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次 Obsidian 导入任务。
 *
 * 状态流转：PENDING → RUNNING → COMPLETED / PARTIAL_FAILED / FAILED；
 * 同用户创建新任务时，未完成的旧任务置 SUPERSEDED；超过 expiredAt 惰性置 EXPIRED。
 */
@Data
@TableName("obsidian_import_task")
public class ObsidianImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的任务号（UUID 去横线） */
    private String taskId;

    private Long userId;

    private String installationId;

    private String status;

    private Integer total;

    private Integer processed;

    private Integer successCount;

    private Integer skippedCount;

    private Integer failedCount;

    private Integer conflictCount;

    private String message;

    /** 交给 AuditMetaObjectHandler 填，避免 updateById 把旧值写回去覆盖 DB 的 ON UPDATE */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime expiredAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
