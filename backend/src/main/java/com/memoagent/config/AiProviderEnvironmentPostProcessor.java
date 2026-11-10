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
 * 按 memoagent.ai.provider 选中的那一套，把地址、密钥、模型名写进 spring.ai.openai.*。
 * 这样切换本地和云端只需要改 application.yml 里的 provider 一个值，不用动启动参数。
 * <p>
 * 执行顺序排在配置文件加载之后，否则读不到 yml 里写的 provider。
 * 写入的 PropertySource 放在最后：配置文件里有同名项时以文件为准，
 * 环境变量和命令行参数的优先级仍然最高。
 */
public class AiProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROVIDER_KEY = "memoagent.ai.provider";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = environment.getProperty(PROVIDER_KEY, "cloud");
        if (!StringUtils.hasText(provider)) {
            return;
        }
        String prefix = "memoagent.ai." + provider + ".";
        String baseUrl = environment.getProperty(prefix + "base-url");
        String model = environment.getProperty(prefix + "model");
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new IllegalStateException("memoagent.ai.provider=" + provider
                    + " 没有对应的配置。provider 目前只支持 cloud 和 local，"
                    + "请检查 " + prefix + "base-url 与 " + prefix + "model");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spring.ai.openai.base-url", baseUrl);
        values.put("spring.ai.openai.chat.options.model", model);
        String apiKey = environment.getProperty(prefix + "api-key");
        if (StringUtils.hasText(apiKey)) {
            values.put("spring.ai.openai.api-key", apiKey);
        }
        String temperature = environment.getProperty(prefix + "temperature");
        if (StringUtils.hasText(temperature)) {
            values.put("spring.ai.openai.chat.options.temperature", temperature);
        }
        environment.getPropertySources().addLast(new MapPropertySource("memoagentAiProvider", values));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 10;
    }
}
