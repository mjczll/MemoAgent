package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryExperience;
import com.memoagent.entity.DiaryKnowledge;
import com.memoagent.entity.DiaryTag;
import com.memoagent.entity.Experience;
import com.memoagent.entity.ExperienceKnowledge;
import com.memoagent.entity.ExperienceTag;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.KnowledgeRelated;
import com.memoagent.entity.KnowledgeTag;
import com.memoagent.entity.Tag;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryExperienceMapper;
import com.memoagent.mapper.DiaryKnowledgeMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.DiaryTagMapper;
import com.memoagent.mapper.ExperienceKnowledgeMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.ExperienceTagMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.mapper.KnowledgeRelatedMapper;
import com.memoagent.mapper.KnowledgeTagMapper;
import com.memoagent.mapper.TagMapper;
import com.memoagent.obsidian.MarkdownRenderer;
import com.memoagent.obsidian.ObsidianVaultLocator;
import com.memoagent.service.ObsidianExportService;
import com.memoagent.vo.ObsidianNoteVO;
import com.memoagent.vo.ObsidianPreviewVO;
import com.memoagent.vo.ObsidianVaultVO;
import com.memoagent.vo.ObsidianWriteResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 单向导出实现：读库 → 渲染 Markdown → 三种取用方式（zip 下载 / JSON 给浏览器 / 直接写本机 Vault）。
 *
 * 渲染保持无状态（同一份数据渲染两次结果完全一致）；写盘是额外的最后一步：
 * 目标 Vault 从 obsidian.json 探测，只往该 Vault 下的 MemoAgent/ 子目录写，
 * 逐字节比对磁盘内容决定是否跳过，不保存「上次写到哪」这类状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObsidianExportServiceImpl implements ObsidianExportService {

    private static final String ROOT = "MemoAgent";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final TagMapper tagMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final ExperienceTagMapper experienceTagMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final DiaryExperienceMapper diaryExperienceMapper;
    private final DiaryKnowledgeMapper diaryKnowledgeMapper;
    private final ExperienceKnowledgeMapper experienceKnowledgeMapper;
    private final KnowledgeRelatedMapper knowledgeRelatedMapper;

    @Override
    public ObsidianPreviewVO preview() {
        Long userId = CurrentUser.id();
        ObsidianPreviewVO vo = new ObsidianPreviewVO();
        vo.setDiaries(Math.toIntExact(diaryMapper.selectCount(
                Wrappers.<Diary>lambdaQuery().eq(Diary::getUserId, userId))));
        vo.setExperiences(Math.toIntExact(experienceMapper.selectCount(
                Wrappers.<Experience>lambdaQuery().eq(Experience::getUserId, userId))));
        vo.setKnowledge(Math.toIntExact(knowledgeMapper.selectCount(
                Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getUserId, userId))));
        // 只算资产篇数：README.md 是导出说明页，不占"篇"，否则界面上的分项加起来对不上总数
        vo.setTotal(vo.getDiaries() + vo.getExperiences() + vo.getKnowledge());
        return vo;
    }

    @Override
    public List<ObsidianNoteVO> notes() {
        Long userId = CurrentUser.id();
        List<Diary> diaries = diaryMapper.selectList(Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getUserId, userId)
                .orderByAsc(Diary::getDiaryDate)
                .orderByAsc(Diary::getId));
        List<Experience> experiences = experienceMapper.selectList(Wrappers.<Experience>lambdaQuery()
                .eq(Experience::getUserId, userId)
                .orderByAsc(Experience::getId));
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(Wrappers.<Knowledge>lambdaQuery()
                .eq(Knowledge::getUserId, userId)
                .orderByAsc(Knowledge::getId));

        Map<Long, String> tagNameById = tagMapper.selectList(null).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (first, second) -> first));

        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        List<Long> experienceIds = experiences.stream().map(Experience::getId).toList();
        List<Long> knowledgeIds = knowledgeList.stream().map(Knowledge::getId).toList();

        Map<Long, List<String>> diaryTags = groupTags(
                diaryIds.isEmpty() ? List.of() : diaryTagMapper.selectList(
                        Wrappers.<DiaryTag>lambdaQuery().in(DiaryTag::getDiaryId, diaryIds)),
                DiaryTag::getDiaryId, DiaryTag::getTagId, tagNameById);
        Map<Long, List<String>> experienceTags = groupTags(
                experienceIds.isEmpty() ? List.of() : experienceTagMapper.selectList(
                        Wrappers.<ExperienceTag>lambdaQuery().in(ExperienceTag::getExperienceId, experienceIds)),
                ExperienceTag::getExperienceId, ExperienceTag::getTagId, tagNameById);
        Map<Long, List<String>> knowledgeTags = groupTags(
                knowledgeIds.isEmpty() ? List.of() : knowledgeTagMapper.selectList(
                        Wrappers.<KnowledgeTag>lambdaQuery().in(KnowledgeTag::getKnowledgeId, knowledgeIds)),
                KnowledgeTag::getKnowledgeId, KnowledgeTag::getTagId, tagNameById);

        Map<Long, List<Long>> diaryExperiences = groupIds(
                diaryIds.isEmpty() ? List.of() : diaryExperienceMapper.selectList(
                        Wrappers.<DiaryExperience>lambdaQuery().in(DiaryExperience::getDiaryId, diaryIds)),
                DiaryExperience::getDiaryId, DiaryExperience::getExperienceId);
        Map<Long, List<Long>> diaryKnowledge = groupIds(
                diaryIds.isEmpty() ? List.of() : diaryKnowledgeMapper.selectList(
                        Wrappers.<DiaryKnowledge>lambdaQuery().in(DiaryKnowledge::getDiaryId, diaryIds)),
                DiaryKnowledge::getDiaryId, DiaryKnowledge::getKnowledgeId);
        Map<Long, List<Long>> experienceKnowledge = groupIds(
                experienceIds.isEmpty() ? List.of() : experienceKnowledgeMapper.selectList(
                        Wrappers.<ExperienceKnowledge>lambdaQuery()
                                .in(ExperienceKnowledge::getExperienceId, experienceIds)),
                ExperienceKnowledge::getExperienceId, ExperienceKnowledge::getKnowledgeId);
        Map<Long, List<Long>> knowledgeRelated = groupIds(
                knowledgeIds.isEmpty() ? List.of() : knowledgeRelatedMapper.selectList(
                        Wrappers.<KnowledgeRelated>lambdaQuery()
                                .in(KnowledgeRelated::getKnowledgeId, knowledgeIds)),
                KnowledgeRelated::getKnowledgeId, KnowledgeRelated::getRelatedId);

        // 第一步：先定文件名。双链必须写目标笔记的文件名，所以渲染前得先有完整映射。
        // 全局去重（跨目录也算），因为 Obsidian 的 [[...]] 是全库范围解析，同名笔记会歧义。
        Set<String> used = new HashSet<>();
        Map<Long, String> diaryNames = new LinkedHashMap<>();
        for (Diary diary : diaries) {
            String prefix = diary.getDiaryDate() == null ? "" : DATE.format(diary.getDiaryDate()) + " ";
            String base = prefix + fileNameOf(diary.getTitle(), "日记");
            diaryNames.put(diary.getId(), register(used, base, diary.getId()));
        }
        Map<Long, String> experienceNames = new LinkedHashMap<>();
        for (Experience experience : experiences) {
            experienceNames.put(experience.getId(),
                    register(used, fileNameOf(experience.getTitle(), "经验"), experience.getId()));
        }
        Map<Long, String> knowledgeNames = new LinkedHashMap<>();
        for (Knowledge knowledge : knowledgeList) {
            knowledgeNames.put(knowledge.getId(),
                    register(used, fileNameOf(knowledge.getTitle(), "知识"), knowledge.getId()));
        }

        // 第二步：渲染。双链用上一步的名字，链接目标不存在时直接丢弃（空链接在 Obsidian 里是噪音）。
        List<ObsidianNoteVO> notes = new ArrayList<>();
        notes.add(note(ROOT + "/README.md", MarkdownRenderer.readme(
                LocalDateTime.now(), diaries.size(), experiences.size(), knowledgeList.size())));

        for (Diary diary : diaries) {
            String content = MarkdownRenderer.diary(diary,
                    diaryTags.get(diary.getId()),
                    links(diaryExperiences.get(diary.getId()), experienceNames),
                    links(diaryKnowledge.get(diary.getId()), knowledgeNames));
            notes.add(note(ROOT + "/Diary/" + diaryNames.get(diary.getId()) + ".md", content));
        }
        for (Experience experience : experiences) {
            String diaryLink = MarkdownRenderer.link(diaryNames.get(experience.getDiaryId()));
            String content = MarkdownRenderer.experience(experience,
                    experienceTags.get(experience.getId()),
                    diaryLink,
                    links(experienceKnowledge.get(experience.getId()), knowledgeNames));
            notes.add(note(ROOT + "/Experience/" + experienceNames.get(experience.getId()) + ".md", content));
        }
        for (Knowledge knowledge : knowledgeList) {
            String content = MarkdownRenderer.knowledge(knowledge,
                    knowledgeTags.get(knowledge.getId()),
                    links(knowledgeRelated.get(knowledge.getId()), knowledgeNames));
            notes.add(note(ROOT + "/Knowledge/" + knowledgeNames.get(knowledge.getId()) + ".md", content));
        }
        return notes;
    }

    @Override
    public byte[] zip() {
        List<ObsidianNoteVO> notes = notes();
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.DEFAULT_COMPRESSION);
            for (ObsidianNoteVO item : notes) {
                zip.putNextEntry(new ZipEntry(item.getPath()));
                zip.write(item.getContent().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            log.info("Obsidian 导出完成：{} 篇，{} KB", notes.size(), buffer.size() / 1024);
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出包生成失败", exception);
        }
    }

    @Override
    public ObsidianVaultVO vault() {
        ObsidianVaultLocator.Result result = ObsidianVaultLocator.locate();
        ObsidianVaultVO vo = new ObsidianVaultVO();
        vo.setConfigFile(result.configFile() == null ? "" : result.configFile().toString());
        vo.setMessage(result.message());
        ObsidianVaultLocator.Option current = result.current();
        vo.setFound(current != null);
        if (current != null) {
            vo.setPath(current.path());
            vo.setName(current.name());
        }
        for (ObsidianVaultLocator.Option option : result.options()) {
            ObsidianVaultVO.Option item = new ObsidianVaultVO.Option();
            item.setPath(option.path());
            item.setName(option.name());
            item.setCurrent(option.current());
            vo.getCandidates().add(item);
        }
        return vo;
    }

    @Override
    public ObsidianWriteResultVO writeToVault(String vaultPath) {
        Path vault = resolveVault(vaultPath);
        Path root = vault.resolve(ROOT).normalize();
        List<ObsidianNoteVO> items = notes();

        ObsidianWriteResultVO result = new ObsidianWriteResultVO();
        result.setVaultPath(vault.toString());
        result.setFolder(ROOT);
        result.setTotal(items.size());

        String firstError = null;
        for (ObsidianNoteVO item : items) {
            Path target = root.resolve(item.getPath().substring(ROOT.length() + 1)).normalize();
            if (!target.startsWith(root)) {
                // 渲染产物里出现越界路径说明有 bug，整单拒绝，绝不写到 Vault 外面
                throw new IllegalStateException("导出路径越界：" + item.getPath());
            }
            try {
                if (writeIfChanged(target, item.getContent())) {
                    result.setWritten(result.getWritten() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
            } catch (IOException exception) {
                // 单篇失败不中断整批：其余笔记照写，最后把失败数报给用户
                log.warn("写入笔记失败：{}", target, exception);
                result.setFailed(result.getFailed() + 1);
                if (firstError == null) {
                    firstError = exception.getMessage();
                }
            }
        }
        if (result.getFailed() > 0) {
            result.setMessage(result.getFailed() + " 篇写入失败" + (firstError == null ? "" : "：" + firstError));
        }
        log.info("Obsidian 写入 {}：写 {} / 跳过 {} / 失败 {}",
                vault, result.getWritten(), result.getSkipped(), result.getFailed());
        return result;
    }

    /**
     * 确定写入目标 Vault。两条硬校验，缺一不可：
     * 1. 路径必须出自 obsidian.json 的登记表——否则这个接口就成了「往本机任意路径写文件」；
     * 2. 目标必须真的含 .obsidian 目录——避免 path 被改坏时把一堆 md 撒进普通文件夹。
     */
    private Path resolveVault(String requestedPath) {
        ObsidianVaultLocator.Result located = ObsidianVaultLocator.locate();
        Path vault;
        if (StringUtils.hasText(requestedPath)) {
            Path wanted = normalize(requestedPath);
            boolean known = located.options().stream()
                    .map(option -> normalize(option.path()))
                    .anyMatch(wanted::equals);
            if (!known) {
                throw BusinessException.badRequest("只能写入 obsidian.json 里登记过的 Vault，收到：" + requestedPath);
            }
            vault = wanted;
        } else {
            ObsidianVaultLocator.Option current = located.current();
            if (current == null) {
                throw BusinessException.badRequest(StringUtils.hasText(located.message())
                        ? located.message() : "未识别到 Obsidian Vault");
            }
            vault = normalize(current.path());
        }
        if (!Files.isDirectory(vault)) {
            throw BusinessException.badRequest("Vault 目录不存在：" + vault);
        }
        if (!Files.isDirectory(vault.resolve(".obsidian"))) {
            throw BusinessException.badRequest("该目录不像 Obsidian Vault（缺少 .obsidian）：" + vault);
        }
        return vault;
    }

    private Path normalize(String value) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw BusinessException.badRequest("路径不合法：" + value);
        }
    }

    /** 内容与磁盘逐字节一致就跳过；返回 true 表示这次真的写了。 */
    private boolean writeIfChanged(Path target, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        // 先写 .tmp 再换名：Obsidian 的文件监听只会看到完整文件，不会读到写了一半的内容
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private <T> Map<Long, List<String>> groupTags(List<T> relations,
                                                  Function<T, Long> owner,
                                                  Function<T, Long> tagId,
                                                  Map<Long, String> tagNameById) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (T relation : relations) {
            String name = tagNameById.get(tagId.apply(relation));
            if (name != null) {
                result.computeIfAbsent(owner.apply(relation), key -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private <T> Map<Long, List<Long>> groupIds(List<T> relations,
                                               Function<T, Long> owner,
                                               Function<T, Long> target) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (T relation : relations) {
            Long targetId = target.apply(relation);
            if (targetId != null) {
                result.computeIfAbsent(owner.apply(relation), key -> new ArrayList<>()).add(targetId);
            }
        }
        return result;
    }

    private List<String> links(List<Long> ids, Map<Long, String> names) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> links = new ArrayList<>();
        for (Long id : ids) {
            String link = MarkdownRenderer.link(names.get(id));
            if (link != null) {
                links.add(link);
            }
        }
        return links;
    }

    private String fileNameOf(String title, String fallback) {
        String name = MarkdownRenderer.safeFileName(title);
        return name.isEmpty() ? fallback : name;
    }

    /**
     * 注册文件名并去重。先按原名，冲突时追加 id 后缀——保证双链指向的名字在全库唯一。
     * 用 toLowerCase 比较：Windows / macOS 默认文件系统大小写不敏感，Obsidian 也会视为同名。
     */
    private String register(Set<String> used, String base, Long id) {
        String candidate = base;
        int suffix = 1;
        while (!used.add(candidate.toLowerCase())) {
            suffix++;
            candidate = base + " (" + id + (suffix > 2 ? "-" + suffix : "") + ")";
        }
        return candidate;
    }

    private ObsidianNoteVO note(String path, String content) {
        ObsidianNoteVO vo = new ObsidianNoteVO();
        vo.setPath(path);
        vo.setContent(content);
        vo.setHash(hash(content));
        return vo;
    }

    /** 内容指纹（SHA-256 前 16 位）：前端直写 Vault 时据此跳过未变化的文件。 */
    private String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }
}
