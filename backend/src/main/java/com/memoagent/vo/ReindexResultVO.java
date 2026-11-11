package com.memoagent.vo;

import lombok.Data;

/** 存量补量的执行结果。补量是同步任务，逐条失败只计数、不中断整批。 */
@Data
public class ReindexResultVO {

    /** 扫描到的 MySQL 资产总数 */
    private int scanned;

    /** 成功写入或更新向量的条数 */
    private int indexed;

    /** 清掉的、MySQL 里已不存在的残留向量条数 */
    private int removed;

    /** 单条写入失败的条数（例如 embedding 调用失败） */
    private int failed;

    private boolean available;

    private String notice;

    public static ReindexResultVO unavailable(String notice) {
        ReindexResultVO result = new ReindexResultVO();
        result.setAvailable(false);
        result.setNotice(notice);
        return result;
    }
}
