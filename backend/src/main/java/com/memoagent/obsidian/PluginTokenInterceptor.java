package com.memoagent.obsidian;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.entity.ObsidianPlugin;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.ObsidianPluginMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 插件接口的 token 鉴权。
 *
 * 只挂需要凭证的路径（/plugin/me、/plugin/tasks/**），配对本身不在此列。
 * token 以 SHA-256 落库，请求时算一遍哈希比对——即使数据库泄露也拿不到可用的 token。
 */
@Component
@RequiredArgsConstructor
public class PluginTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Plugin-Token";

    private final ObsidianPluginMapper pluginMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Tomcat 线程复用：上一次请求若异常退出没清理，这里先兜底清掉
        PluginContext.clear();

        String token = request.getHeader(HEADER);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "缺少插件凭证，请在 Obsidian 插件里完成配对");
        }

        ObsidianPlugin plugin = pluginMapper.selectOne(Wrappers.<ObsidianPlugin>lambdaQuery()
                .eq(ObsidianPlugin::getTokenHash, Digests.sha256(token.trim()))
                .eq(ObsidianPlugin::getRevoked, 0)
                .last("limit 1"));
        if (plugin == null) {
            throw new BusinessException(401, "插件凭证无效，请重新配对");
        }

        PluginContext.set(plugin);
        touch(plugin);
        return true;
    }

    /**
     * 心跳：任何带 token 的插件请求都算「这台机器还活着」，前端据此显示在线/离线。
     *
     * 以前只有 /me 更新 lastActiveAt，而插件平时只领任务、不调 /me，
     * 结果配对三分钟后前端就必然显示「离线」。下沉到这里，长轮询、上报进度都能续期。
     * 节流到一分钟一次，避免每个请求都写库。
     */
    private void touch(ObsidianPlugin plugin) {
        LocalDateTime now = LocalDateTime.now();
        if (plugin.getLastActiveAt() != null && plugin.getLastActiveAt().isAfter(now.minusMinutes(1))) {
            return;
        }
        plugin.setLastActiveAt(now);
        pluginMapper.updateById(plugin);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        PluginContext.clear();
    }
}
