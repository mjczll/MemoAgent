package com.memoagent.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.entity.Diary;
import com.memoagent.entity.DiaryExperience;
import com.memoagent.entity.DiaryKnowledge;
import com.memoagent.entity.DiaryTag;
import com.memoagent.entity.Experience;
import com.memoagent.entity.ExperienceKnowledge;
import com.memoagent.entity.Knowledge;
import com.memoagent.entity.KnowledgeRelated;
import com.memoagent.entity.Tag;
import com.memoagent.exception.BusinessException;
import com.memoagent.mapper.DiaryExperienceMapper;
import com.memoagent.mapper.DiaryKnowledgeMapper;
import com.memoagent.mapper.DiaryMapper;
import com.memoagent.mapper.DiaryTagMapper;
import com.memoagent.mapper.ExperienceKnowledgeMapper;
import com.memoagent.mapper.ExperienceMapper;
import com.memoagent.mapper.KnowledgeMapper;
import com.memoagent.mapper.KnowledgeRelatedMapper;
import com.memoagent.mapper.TagMapper;
import com.memoagent.service.GraphService;
import com.memoagent.vo.GraphEdgeVO;
import com.memoagent.vo.GraphNodeVO;
import com.memoagent.vo.GraphVO;

import lombok.RequiredArgsConstructor;

/**
 * 织网实现。规则从 fronted/src/lib/graph.ts 逐行移植：
 *
 * 边语义：提炼（日记→经验）、沉淀（经验→知识）、关联（日记→知识）、相关（知识↔知识）、归属（实体→领域）
 * 权重：领域 8+条数×1.6、知识 4+互链×0.9+来源经验×0.9、经验 5+知识×0.9、日记 3.5+关联×0.7
 *
 * 与旧版的两处差异（均为已拍板的决策）：
 * 1. 领域不再依赖前端 DOMAINS mock，直接聚合数据的真实 domain；
 * 2. 日记领域取首个标签，无标签记为「未分类」（旧版硬编码兜底「项目开发」已废除）。
 */
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private static final String DOMAIN_PREFIX = "domain:";
    private static final String UNCLASSIFIED = "未分类";

    private final DiaryMapper diaryMapper;
    private final ExperienceMapper experienceMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final DiaryExperienceMapper diaryExperienceMapper;
    private final DiaryKnowledgeMapper diaryKnowledgeMapper;
    private final ExperienceKnowledgeMapper experienceKnowledgeMapper;
    private final KnowledgeRelatedMapper knowledgeRelatedMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;

    @Override
    public GraphVO graph(String domain) {
        GraphVO full = buildFullGraph();
        if (domain == null || domain.isBlank() || "all".equalsIgnoreCase(domain)) {
            return full;
        }
        return subgraph(full, node -> domain.equals(node.getDomain()) || domain.equals(node.getLabel()));
    }

    @Override
    public GraphVO local(String type, Long id, int depth) {
        String centerId = requireCenter(type, id);
        GraphVO full = buildFullGraph();
        // BFS：从中心节点走 depth 跳
        Map<String, Integer> distance = new HashMap<>();
        List<String> queue = new ArrayList<>();
        distance.put(centerId, 0);
        queue.add(centerId);
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (GraphEdgeVO edge : full.getEdges()) {
            adjacency.computeIfAbsent(edge.getSource(), key -> new LinkedHashSet<>()).add(edge.getTarget());
            adjacency.computeIfAbsent(edge.getTarget(), key -> new LinkedHashSet<>()).add(edge.getSource());
        }
        for (int head = 0; head < queue.size(); head++) {
            String current = queue.get(head);
            int currentDepth = distance.get(current);
            if (currentDepth >= depth) {
                continue;
            }
            for (String neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }
        return subgraph(full, node -> distance.containsKey(node.getId()));
    }

    private interface NodePredicate {
        boolean test(GraphNodeVO node);
    }

    /** 保留满足条件的节点及其间的边。 */
    private GraphVO subgraph(GraphVO full, NodePredicate keep) {
        GraphVO result = new GraphVO();
        Set<String> kept = new HashSet<>();
        for (GraphNodeVO node : full.getNodes()) {
            if (keep.test(node)) {
                result.getNodes().add(node);
                kept.add(node.getId());
            }
        }
        for (GraphEdgeVO edge : full.getEdges()) {
            if (kept.contains(edge.getSource()) && kept.contains(edge.getTarget())) {
                result.getEdges().add(edge);
            }
        }
        return result;
    }

    /** 三种实体共用自增主键，节点 id 必须带类型前缀；局部图入口必须指明类型。 */
    private static final Set<String> CENTER_TYPES = Set.of("diary", "experience", "knowledge");

    /** 校验指定类型的中心实体属于当前用户，返回带类型前缀的节点 id。 */
    private String requireCenter(String type, Long id) {
        if (!CENTER_TYPES.contains(type)) {
            throw BusinessException.badRequest("未知的节点类型：" + type);
        }
        Long userId = com.memoagent.common.CurrentUser.id();
        boolean owned = switch (type) {
            case "diary" -> diaryMapper.selectCount(Wrappers.<Diary>lambdaQuery().eq(Diary::getId, id).eq(Diary::getUserId, userId)) > 0;
            case "experience" -> experienceMapper.selectCount(Wrappers.<Experience>lambdaQuery().eq(Experience::getId, id).eq(Experience::getUserId, userId)) > 0;
            default -> knowledgeMapper.selectCount(Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getId, id).eq(Knowledge::getUserId, userId)) > 0;
        };
        if (!owned) {
            throw BusinessException.notFound("图谱中心节点不存在");
        }
        return type + ":" + id;
    }

    /** 全图织网：三份列表 + 四张关联表 + 标签，内存组装。 */
    private GraphVO buildFullGraph() {
        Long userId = com.memoagent.common.CurrentUser.id();

        List<Diary> diaries = diaryMapper.selectList(
                Wrappers.<Diary>lambdaQuery().eq(Diary::getUserId, userId).orderByDesc(Diary::getId));
        List<Experience> experiences = experienceMapper.selectList(
                Wrappers.<Experience>lambdaQuery().eq(Experience::getUserId, userId).orderByDesc(Experience::getId));
        List<Knowledge> knowledge = knowledgeMapper.selectList(
                Wrappers.<Knowledge>lambdaQuery().eq(Knowledge::getUserId, userId).orderByDesc(Knowledge::getId));

        List<DiaryExperience> diaryExperiences = diaryExperienceMapper.selectList(
                Wrappers.<DiaryExperience>lambdaQuery());
        List<DiaryKnowledge> diaryKnowledges = diaryKnowledgeMapper.selectList(
                Wrappers.<DiaryKnowledge>lambdaQuery());
        List<ExperienceKnowledge> experienceKnowledges = experienceKnowledgeMapper.selectList(
                Wrappers.<ExperienceKnowledge>lambdaQuery());
        List<KnowledgeRelated> knowledgeRelateds = knowledgeRelatedMapper.selectList(
                Wrappers.<KnowledgeRelated>lambdaQuery());

        // 日记领域 = 首个标签名（无标签记「未分类」）
        Map<Long, String> domainByDiary = diaryDomainByFirstTag(diaries);

        // 关联集合：key = 实体 id
        Set<Long> diaryIds = toIds(diaries);
        Set<Long> experienceIds = toIds(experiences);
        Set<Long> knowledgeIds = toIds(knowledge);

        Map<Long, Set<Long>> experienceIdsByDiary = groupTargets(diaryExperiences, DiaryExperience::getDiaryId,
                DiaryExperience::getExperienceId, diaryIds);
        Map<Long, Set<Long>> knowledgeIdsByDiary = groupTargets(diaryKnowledges, DiaryKnowledge::getDiaryId,
                DiaryKnowledge::getKnowledgeId, diaryIds);
        Map<Long, Set<Long>> knowledgeIdsByExperience = groupTargets(experienceKnowledges, ExperienceKnowledge::getExperienceId,
                ExperienceKnowledge::getKnowledgeId, experienceIds);
        Map<Long, Set<Long>> experienceIdsByKnowledge = groupTargets(experienceKnowledges, ExperienceKnowledge::getKnowledgeId,
                ExperienceKnowledge::getExperienceId, knowledgeIds);

        GraphVO graph = new GraphVO();
        Set<String> nodeIds = new LinkedHashSet<>();
        Map<String, GraphEdgeVO> edgeByKey = new LinkedHashMap<>();

        // 领域节点：聚合经验 / 知识的真实 domain（日记领域算条目数不计入，与旧版一致只算知识 + 经验）
        Map<String, Integer> domainCount = new LinkedHashMap<>();
        for (Knowledge item : knowledge) {
            domainCount.merge(domainOf(item.getDomain()), 1, Integer::sum);
        }
        for (Experience item : experiences) {
            domainCount.merge(domainOf(item.getDomain()), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : domainCount.entrySet()) {
            GraphNodeVO node = new GraphNodeVO();
            node.setId(DOMAIN_PREFIX + entry.getKey());
            node.setLabel(entry.getKey());
            node.setType("domain");
            node.setDomain(entry.getKey());
            node.setRefId(entry.getKey());
            node.setVisibility("private");
            node.setWeight(8 + entry.getValue() * 1.6);
            graph.getNodes().add(node);
            nodeIds.add(node.getId());
        }

        for (Knowledge item : knowledge) {
            String domain = domainOf(item.getDomain());
            GraphNodeVO node = new GraphNodeVO();
            node.setId("knowledge:" + item.getId());
            node.setLabel(item.getTitle());
            node.setType("knowledge");
            node.setDomain(domain);
            node.setRefId(String.valueOf(item.getId()));
            node.setVisibility(item.getVisibility());
            node.setWeight(4 + relatedCount(knowledgeRelateds, item.getId()) * 0.9
                    + experienceIdsByKnowledge.getOrDefault(item.getId(), Set.of()).size() * 0.9);
            node.setUpdatedAt(iso(item.getUpdatedAt()));
            graph.getNodes().add(node);
            nodeIds.add(node.getId());
        }

        for (Experience item : experiences) {
            String domain = domainOf(item.getDomain());
            GraphNodeVO node = new GraphNodeVO();
            node.setId("experience:" + item.getId());
            node.setLabel(item.getTitle());
            node.setType("experience");
            node.setDomain(domain);
            node.setRefId(String.valueOf(item.getId()));
            node.setVisibility(item.getVisibility());
            node.setWeight(5 + knowledgeIdsByExperience.getOrDefault(item.getId(), Set.of()).size() * 0.9);
            node.setUpdatedAt(iso(item.getCreatedAt()));
            graph.getNodes().add(node);
            nodeIds.add(node.getId());
        }

        for (Diary item : diaries) {
            String domain = domainByDiary.getOrDefault(item.getId(), UNCLASSIFIED);
            GraphNodeVO node = new GraphNodeVO();
            node.setId("diary:" + item.getId());
            node.setLabel(item.getTitle());
            node.setType("diary");
            node.setDomain(domain);
            node.setRefId(String.valueOf(item.getId()));
            // diary 表没有 visibility 列，服务层一直按 private 输出，沿用该语义
            node.setVisibility("private");
            int links = experienceIdsByDiary.getOrDefault(item.getId(), Set.of()).size()
                    + knowledgeIdsByDiary.getOrDefault(item.getId(), Set.of()).size();
            node.setWeight(3.5 + links * 0.7);
            node.setUpdatedAt(iso(item.getCreatedAt()));
            graph.getNodes().add(node);
            nodeIds.add(node.getId());
        }

        // 日记 → 经验：提炼（关联表正向 + 经验上 diaryId 反向补边，与旧版一致）
        for (DiaryExperience link : diaryExperiences) {
            addEdge(edgeByKey, nodeIds, "diary:" + link.getDiaryId(), "experience:" + link.getExperienceId(), "提炼");
        }
        for (Experience item : experiences) {
            if (item.getDiaryId() != null && diaryIds.contains(item.getDiaryId())) {
                addEdge(edgeByKey, nodeIds, "diary:" + item.getDiaryId(), "experience:" + item.getId(), "提炼");
            }
        }

        // 经验 → 知识：沉淀
        for (ExperienceKnowledge link : experienceKnowledges) {
            addEdge(edgeByKey, nodeIds, "experience:" + link.getExperienceId(), "knowledge:" + link.getKnowledgeId(), "沉淀");
        }

        // 日记 → 知识：关联（经验上已挂的知识也算到它所属的日记，与旧版一致）
        for (DiaryKnowledge link : diaryKnowledges) {
            addEdge(edgeByKey, nodeIds, "diary:" + link.getDiaryId(), "knowledge:" + link.getKnowledgeId(), "关联");
        }
        for (Experience item : experiences) {
            if (item.getDiaryId() == null || !diaryIds.contains(item.getDiaryId())) {
                continue;
            }
            for (Long knowledgeId : knowledgeIdsByExperience.getOrDefault(item.getId(), Set.of())) {
                addEdge(edgeByKey, nodeIds, "diary:" + item.getDiaryId(), "knowledge:" + knowledgeId, "关联");
            }
        }

        // 知识 ↔ 知识：相关
        for (KnowledgeRelated link : knowledgeRelateds) {
            addEdge(edgeByKey, nodeIds, "knowledge:" + link.getKnowledgeId(), "knowledge:" + link.getRelatedId(), "相关");
        }

        // 实体 → 领域：归属（只挂经验与知识，与旧版一致）
        for (Knowledge item : knowledge) {
            addEdge(edgeByKey, nodeIds, "knowledge:" + item.getId(), DOMAIN_PREFIX + domainOf(item.getDomain()), "归属");
        }
        for (Experience item : experiences) {
            addEdge(edgeByKey, nodeIds, "experience:" + item.getId(), DOMAIN_PREFIX + domainOf(item.getDomain()), "归属");
        }

        graph.setEdges(new ArrayList<>(edgeByKey.values()));
        return graph;
    }

    private void addEdge(Map<String, GraphEdgeVO> edges, Set<String> nodeIds, String source, String target, String label) {
        if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
            return;
        }
        String key = source + "|" + target + "|" + label;
        if (edges.containsKey(key)) {
            return;
        }
        GraphEdgeVO edge = new GraphEdgeVO();
        edge.setId("ge-" + (edges.size() + 1));
        edge.setSource(source);
        edge.setTarget(target);
        edge.setLabel(label);
        edges.put(key, edge);
    }

    /** 相关边的计数：related 双向都算（与前端 relatedIds 的加载语义一致）。 */
    private int relatedCount(List<KnowledgeRelated> links, Long knowledgeId) {
        Set<Long> ids = new HashSet<>();
        for (KnowledgeRelated link : links) {
            if (link.getKnowledgeId().equals(knowledgeId)) {
                ids.add(link.getRelatedId());
            } else if (link.getRelatedId().equals(knowledgeId)) {
                ids.add(link.getKnowledgeId());
            }
        }
        return ids.size();
    }

    /** 日记领域 = 首个标签名；无标签记「未分类」。 */
    private Map<Long, String> diaryDomainByFirstTag(List<Diary> diaries) {
        if (diaries.isEmpty()) {
            return Map.of();
        }
        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        List<DiaryTag> relations = diaryTagMapper.selectList(
                Wrappers.<DiaryTag>lambdaQuery().in(DiaryTag::getDiaryId, diaryIds).orderByAsc(DiaryTag::getId));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> tagIds = relations.stream().map(DiaryTag::getTagId).distinct().toList();
        Map<Long, String> nameById = new HashMap<>();
        for (Tag tag : tagMapper.selectByIds(tagIds)) {
            nameById.put(tag.getId(), tag.getName());
        }
        // 首个标签按关联 id 升序（与 tagSummary 的挂载顺序一致）
        Map<Long, String> result = new HashMap<>();
        for (DiaryTag relation : relations) {
            String name = nameById.get(relation.getTagId());
            if (name != null && !result.containsKey(relation.getDiaryId())) {
                result.put(relation.getDiaryId(), name);
            }
        }
        return result;
    }

    private <T> Map<Long, Set<Long>> groupTargets(List<T> links,
            java.util.function.Function<T, Long> sourceKey,
            java.util.function.Function<T, Long> targetKey,
            Set<Long> allowedSources) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (T link : links) {
            Long source = sourceKey.apply(link);
            if (!allowedSources.contains(source)) {
                continue;
            }
            result.computeIfAbsent(source, key -> new HashSet<>()).add(targetKey.apply(link));
        }
        return result;
    }

    private Set<Long> toIds(List<? extends Object> rows) {
        Set<Long> ids = new HashSet<>();
        for (Object row : rows) {
            if (row instanceof Diary diary) {
                ids.add(diary.getId());
            } else if (row instanceof Experience experience) {
                ids.add(experience.getId());
            } else if (row instanceof Knowledge knowledge) {
                ids.add(knowledge.getId());
            }
        }
        return ids;
    }

    private String domainOf(String domain) {
        return domain == null || domain.isBlank() ? UNCLASSIFIED : domain.trim();
    }

    private String iso(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
