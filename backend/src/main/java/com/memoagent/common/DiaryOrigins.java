package com.memoagent.common;

import com.memoagent.exception.BusinessException;

public final class DiaryOrigins {

    public static final String MANUAL = "manual";
    public static final String INTERVIEW = "interview";

    private DiaryOrigins() {
    }

    public static String normalize(String origin) {
        if (origin == null || origin.isBlank()) {
            return MANUAL;
        }
        String value = origin.trim();
        if (!MANUAL.equals(value) && !INTERVIEW.equals(value)) {
            throw BusinessException.badRequest("不支持的日记来源");
        }
        return value;
    }
}
