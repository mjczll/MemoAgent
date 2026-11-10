package com.memoagent.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class PromptFiles {

    private PromptFiles() {
    }

    static String load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("缺少提示词 " + path);
        }
        try (InputStream input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("缺少提示词 " + path, exception);
        }
    }
}
