package com.memoagent.vector;

import java.util.Set;

/**
 * 向量来源类型。取值与 MySQL 表名一致，也是 vector_asset.source_type 的取值。
 * 之前这几个字符串散落在仓储白名单和召回代码里，统一放这里，避免业务侧写错类型后
 * 要到写库时才被 requireType 拦下。
 */
public final class VectorSourceTypes {

    public static final String DIARY = "diary";

    public static final String EXPERIENCE = "experience";

    public static final String KNOWLEDGE = "knowledge";

    public static final Set<String> ALL = Set.of(DIARY, EXPERIENCE, KNOWLEDGE);

    private VectorSourceTypes() {
    }
}
