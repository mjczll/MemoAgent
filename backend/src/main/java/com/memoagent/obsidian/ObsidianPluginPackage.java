package com.memoagent.obsidian;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Obsidian 插件的唯一副本。
 *
 * main.js 与 manifest.json 直接放在 resources 下随 jar 发布：插件是手写的 CommonJS，
 * 不经过 TypeScript 编译、不需要 npm 打包，所以后端自己就能发 zip、也能直接写进本机 Vault。
 *
 * 这样用户侧只有两种「装插件」的方式，都不涉及命令行：
 * - 后端与 Obsidian 同机：直接写进 .obsidian/plugins/memoagent/
 * - 后端在云端：下载 zip，解压后把 memoagent 文件夹拖进 .obsidian/plugins/
 */
@Slf4j
@Component
public class ObsidianPluginPackage {

    /** 插件目录名，必须与 manifest.json 的 id 一致 */
    public static final String FOLDER = "memoagent";

    private static final String RESOURCE_DIR = "obsidian-plugin/";
    private static final List<String> FILES = List.of("manifest.json", "main.js");

    private static final String README = """
            MemoAgent for Obsidian — 安装说明
            =====================================

            1. 解压后你会看到一个 memoagent 文件夹。
            2. 把它整个放进  <你的 Vault>/.obsidian/plugins/  目录下（plugins 里有其它插件就放一起）。
            3. 重启 Obsidian，或按 Ctrl+P 执行 "Reload app without saving"。
            4. 打开 设置 → 第三方插件，确认 MemoAgent 已启用
               （若 Obsidian 处于「受限模式」，先关闭它）。
            5. 回到 MemoAgent 网页，点「连接 Obsidian」，浏览器会问是否打开 Obsidian，选允许。
               插件收到参数后会自动连接，不需要手抄配对码。

            连不上时：在插件设置里手动填网页上的 6 位配对码，效果相同。

            说明：这是单向导入。MemoAgent 的日记/经验/知识会写进 Vault 的 MemoAgent/ 目录，
            在 Obsidian 里改的内容不会回流到 MemoAgent。
            """;

    /** 下载用：zip 里是一个 memoagent/ 文件夹 + 说明，解压即可拖进 plugins。 */
    public byte[] zip() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (String name : FILES) {
                zip.putNextEntry(new ZipEntry(FOLDER + "/" + name));
                zip.write(read(name));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("安装说明.txt"));
            zip.write(README.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException exception) {
            throw new IllegalStateException("生成插件包失败", exception);
        }
        return buffer.toByteArray();
    }

    /**
     * 写进 {@code <Vault>/.obsidian/plugins/memoagent/}。
     * 原地覆盖，所以这条同时也是插件升级路径——后端换版本后重装一次即可。
     */
    public Path writeTo(Path pluginDir) throws IOException {
        Files.createDirectories(pluginDir);
        for (String name : FILES) {
            Files.write(pluginDir.resolve(name), read(name));
        }
        return pluginDir;
    }

    private byte[] read(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_DIR + name);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }
}
