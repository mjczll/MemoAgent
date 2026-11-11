package com.memoagent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把两组独立的选型写进 spring.ai.openai.*：
 * - 对话模型：memoagent.ai.provider 选 cloud / local
 * - 向量模型：memoagent.embedding.provider 选 cloud / local，与对话模型互不绑定
 * <p>
 * Spring AI 1.0.3 允许 spring.ai.openai.embedding.base-url / api-key 覆盖全局连接属性，
 * 因此「对话走云端、向量走本机 Ollama」这类混搭只靠配置就能成立，不需要额外注册 Bean。
 * <p>
 * 执行顺序排在配置文件加载之后，否则读不到 yml 里的选型。
 * 写入的 PropertySource 放在最后：配置文件里有同名项时以文件为准，
 * 环境变量和命令行参数的优先级仍然最高。
 */
public class AiProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String AI_PROVIDER_KEY = "memoagent.ai.provider";

    private static final String EMBEDDING_PROVIDER_KEY = "memoagent.embedding.provider";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> values = new LinkedHashMap<>();
        applyChat(environment, values);
        applyEmbedding(environment, values);
        environment.getPropertySources().addLast(new MapPropertySource("memoagentAiProvider", values));
    }

    /** 对话模型：地址、密钥、模型名、温度。地址会同时作为 embedding 的兜底地址。 */
    private void applyChat(ConfigurableEnvironment environment, Map<String, Object> values) {
        String provider = environment.getProperty(AI_PROVIDER_KEY, "cloud");
        String prefix = "memoagent.ai." + provider + ".";
        String baseUrl = environment.getProperty(prefix + "base-url");
        String model = environment.getProperty(prefix + "model");
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new IllegalStateException("memoagent.ai.provider=" + provider
                    + " 没有对应的配置。provider 目前只支持 cloud 和 local，"
                    + "请检查 " + prefix + "base-url 与 " + prefix + "model");
        }
        values.put("spring.ai.openai.base-url", baseUrl);
        values.put("spring.ai.openai.chat.options.model", model);
        putIfPresent(environment, prefix + "api-key", "spring.ai.openai.api-key", values);
        putIfPresent(environment, prefix + "temperature", "spring.ai.openai.chat.options.temperature", values);
    }

    /**
     * 向量模型：模型名必填，地址与密钥可选。
     * 不写专属地址时沿用 applyChat 设的全局地址，不写专属密钥时沿用 profile 里的全局密钥。
     * 本地 Ollama 两项都显式写了，所以即使对话走云端，embedding 仍然打本机 11434。
     */
    private void applyEmbedding(ConfigurableEnvironment environment, Map<String, Object> values) {
        String provider = environment.getProperty(EMBEDDING_PROVIDER_KEY, "local");
        String prefix = "memoagent.embedding." + provider + ".";
        String model = environment.getProperty(prefix + "model");
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("memoagent.embedding.provider=" + provider
                    + " 没有配置模型，请检查 " + prefix + "model");
        }
        // 云端 embedding（默认百炼）用的是自己的 DASHSCOPE_API_KEY。
        // 这里不让空值悄悄回退到对话接口的全局 key，否则只会在第一次写入向量时拿到一个难查的 401。
        String apiKey = environment.getProperty(prefix + "api-key");
        if ("cloud".equals(provider) && !StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("memoagent.embedding.provider=cloud 但没有配置专属 api-key。"
                    + "请设置 MEMOAGENT_EMBEDDING_API_KEY（或 DASHSCOPE_API_KEY）；"
                    + "若 embedding 与对话确实共用同一密钥，在 memoagent.embedding.cloud.api-key 显式填同一个值");
        }
        values.put("spring.ai.openai.embedding.options.model", model);
        putIfPresent(environment, prefix + "base-url", "spring.ai.openai.embedding.base-url", values);
        putIfPresent(environment, prefix + "api-key", "spring.ai.openai.embedding.api-key", values);
    }

    private void putIfPresent(ConfigurableEnvironment environment, String sourceKey,
                              String targetKey, Map<String, Object> values) {
        String value = environment.getProperty(sourceKey);
        if (StringUtils.hasText(value)) {
            values.put(targetKey, value);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 10;
    }
}
