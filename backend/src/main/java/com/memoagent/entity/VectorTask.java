package com.memoagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量写入补偿任务。表结构见 db/vector-task.sql。
 * <p>
 * 一份资产只保留一行（source_type + source_id 唯一），避免反复保存时队列膨胀。
 */
@Data
@TableName("vector_task")
public class VectorTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** diary / experience / knowledge */
    private String sourceType;

    private Long sourceId;

    /** 已重试次数，0 表示还没重试过 */
    private Integer attempts;

    /** pending 待重试 / abandoned 已放弃 */
    private String status;

    /** 最后一次失败的异常摘要，保留最近一次即可 */
    private String lastError;

    /** 下次可重试时间，按退避策略推进 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
