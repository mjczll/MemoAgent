package com.memoagent.config;

import com.memoagent.obsidian.PluginTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 插件鉴权的挂载点。
 *
 * 只拦需要 token 的三条路径。配对接口（/plugin/pair、/plugin/pair-codes）不能拦，
 * 否则插件还没拿到 token 就进不来，形成死锁。
 */
@Configuration
@RequiredArgsConstructor
public class PluginAuthConfig implements WebMvcConfigurer {

    private final PluginTokenInterceptor pluginTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pluginTokenInterceptor)
                .addPathPatterns(
                        "/api/obsidian/plugin/me",
                        "/api/obsidian/plugin/tasks/**");
    }
}
