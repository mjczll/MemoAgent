package com.memoagent.obsidian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 从 Obsidian 自己的配置文件 obsidian.json 里找出「当前打开」的 Vault 目录。
 *
 * 原理：Obsidian 每次打开一个 Vault 都会把它登记进 obsidian.json 的 vaults 表，
 * 当前窗口对应的那条带 "open": true，并记着最后打开时间 ts。后端跑在本机，
 * 直接读这个文件就能拿到绝对路径，用户不需要在浏览器里手选目录。
 *
 * 为什么必须由后端做：浏览器的 File System Access API 只接受用户手势选择出来的目录，
 * 没有任何「传一个路径字符串就打开该目录」的接口，前端也读不到 %APPDATA%。
 *
 * 安全边界：本类只负责「读出候选路径」，不做任何写入；写入方（ObsidianExportServiceImpl）
 * 会再校验目标必须是这里登记过的 Vault，且必须真的含 .obsidian 目录。
 */
@Slf4j
public final class ObsidianVaultLocator {

    /** 允许用环境变量指到别处的配置（便携版、多用户装机、测试） */
    public static final String ENV_OVERRIDE = "MEMOAGENT_OBSIDIAN_JSON";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObsidianVaultLocator() {
    }

    /** 一个登记在 obsidian.json 里的 Vault。ts 只用于排序，不对外暴露。 */
    public record Option(String path, String name, boolean current, long ts) {
    }

    /**
     * 探测结果。configFile 与 message 会显示在设置页，识别失败时用户能自己判断原因
     * （没装 Obsidian / 配置在别处 / 手工改坏了 JSON）。
     */
    public record Result(Path configFile, List<Option> options, String message) {

        /** 首选 Vault：open=true 里最后打开的那个。 */
        public Option current() {
            return options.isEmpty() ? null : options.get(0);
        }

        public boolean found() {
            return current() != null;
        }
    }

    public static Result locate() {
        Path config = configFile();
        if (config == null || !Files.isRegularFile(config)) {
            return new Result(config, List.of(),
                    "没找到 obsidian.json，可能是本机没装 Obsidian，或从未打开过 Vault");
        }

        JsonNode root;
        try (InputStream in = Files.newInputStream(config)) {
            root = MAPPER.readTree(in);
        } catch (IOException exception) {
            log.warn("读取 obsidian.json 失败：{}", config, exception);
            return new Result(config, List.of(), "读取 obsidian.json 失败：" + exception.getMessage());
        }

        JsonNode vaults = root.path("vaults");
        if (!vaults.isObject() || vaults.isEmpty()) {
            return new Result(config, List.of(), "obsidian.json 里还没有登记任何 Vault");
        }

        List<Option> options = new ArrayList<>();
        vaults.fields().forEachRemaining(entry -> {
            String raw = entry.getValue().path("path").asText("");
            if (!StringUtils.hasText(raw)) {
                return;
            }
            Path dir = toPath(raw);
            if (dir == null) {
                log.warn("obsidian.json 里的路径不合法，已跳过：{}", raw);
                return;
            }
            options.add(new Option(
                    dir.toString(),
                    dir.getFileName() == null ? raw : dir.getFileName().toString(),
                    entry.getValue().path("open").asBoolean(false),
                    entry.getValue().path("ts").asLong(0L)));
        });

        if (options.isEmpty()) {
            return new Result(config, List.of(), "obsidian.json 里的 Vault 都缺少可用的 path");
        }
        // 当前打开的排最前；其余按最后打开时间倒序，最近用过的优先
        options.sort(Comparator.comparingInt((Option option) -> option.current() ? 1 : 0).reversed()
                .thenComparing(Comparator.comparingLong(Option::ts).reversed()));
        return new Result(config, options, "");
    }

    /** obsidian.json 的位置，按操作系统惯例推断。 */
    public static Path configFile() {
        String override = System.getenv(ENV_OVERRIDE);
        if (StringUtils.hasText(override)) {
            return safePath(override);
        }
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = StringUtils.hasText(appData)
                    ? safePath(appData)
                    : safePath(home + "/AppData/Roaming");
            return base == null ? null : base.resolve("obsidian").resolve("obsidian.json");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", "obsidian", "obsidian.json");
        }
        return Path.of(home, ".config", "obsidian", "obsidian.json");
    }

    /**
     * 把配置里的字符串转成绝对路径。
     * 少数情况下 Obsidian 会把空格等字符写成 %20，因此目录不存在时再按 URL 解码试一次。
     */
    private static Path toPath(String raw) {
        Path direct = safePath(raw);
        if (direct != null && Files.isDirectory(direct)) {
            return direct;
        }
        if (direct == null) {
            return null;
        }
        if (raw.indexOf('%') >= 0) {
            try {
                Path decoded = safePath(URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8));
                if (decoded != null && Files.isDirectory(decoded)) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // 不是合法编码，保留原样
            }
        }
        return direct;
    }

    private static Path safePath(String value) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            log.warn("非法路径：{}", value);
            return null;
        }
    }
}
