package com.memoagent.obsidian;

import com.memoagent.entity.ObsidianPlugin;
import com.memoagent.exception.BusinessException;

/**
 * 当前请求的插件实例。由 {@link PluginTokenInterceptor} 在 preHandle 里放入，
 * 请求结束后清理（Tomcat 线程复用，不清理会串号）。
 */
public final class PluginContext {

    private static final ThreadLocal<ObsidianPlugin> HOLDER = new ThreadLocal<>();

    private PluginContext() {
    }

    public static void set(ObsidianPlugin plugin) {
        HOLDER.set(plugin);
    }

    /** 拦截器已保证存在；直接调用处拿不到说明路由配置漏了鉴权，按 401 处理而不是放行 */
    public static ObsidianPlugin get() {
        ObsidianPlugin plugin = HOLDER.get();
        if (plugin == null) {
            throw new BusinessException(401, "插件凭证缺失");
        }
        return plugin;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
