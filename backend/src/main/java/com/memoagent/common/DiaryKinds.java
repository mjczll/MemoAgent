package com.memoagent.common;

import com.memoagent.exception.BusinessException;
import org.springframework.util.StringUtils;

public final class DiaryKinds {

    private DiaryKinds() {
    }

    public static String normalize(String kind) {
        if (!StringUtils.hasText(kind)) {
            throw BusinessException.badRequest("类型不能为空");
        }
        String name = kind.trim();
        if (name.length() > 32) {
            throw BusinessException.badRequest("类型名称不能超过32字");
        }
        return name;
    }
}
