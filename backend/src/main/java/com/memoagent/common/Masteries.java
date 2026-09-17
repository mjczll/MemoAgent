package com.memoagent.common;

import com.memoagent.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class Masteries {

    public static final String PENDING = "待补充";
    public static final String PARTIAL = "部分掌握";
    public static final String MASTERED = "已掌握";

    private static final Set<String> ALL = Set.of(PENDING, PARTIAL, MASTERED);

    private Masteries() {
    }

    public static String normalize(String mastery) {
        if (!StringUtils.hasText(mastery)) {
            return PENDING;
        }
        String value = mastery.trim();
        if (!ALL.contains(value)) {
            throw BusinessException.badRequest("掌握度不合法");
        }
        return value;
    }
}
