package com.memoagent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 未手动指定配置时：有 application-dev.yml 用开发版，没有则用发行版。
 */
public class ProfileFallbackEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("spring.profiles.active"))) {
            return;
        }
        boolean devPresent = getClass().getClassLoader().getResource("application-dev.yml") != null;
        String profile = devPresent ? "dev" : "prod";
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "memoagentProfileFallback",
                Map.of("spring.profiles.active", profile)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
