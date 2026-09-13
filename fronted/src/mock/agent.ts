import type { AgentRef } from "@/types";

export interface AgentPreset {
  /** 触发该预设所需命中的关键词（全部命中才算） */
  markers: string[];
  question: string;
  answer: string;
  trace: string[];
  refs: AgentRef[];
}

/** Agent 页的推荐问题（点击即发送） */
export const AGENT_SUGGESTIONS: string[] = [
  "我之前是怎么解决 Redis 缓存击穿的？",
  "我在 Spring AI 上踩过哪些坑？",
  "总结一下我在 MySQL 索引优化上的经验。",
  "我以前有没有遇到过类似的问题？",
];

export const AGENT_GREETING =
  "我是你的经验 Agent。我读过你写的所有日记、沉淀的经验和知识条目 —— 你上一次是怎么解决某个问题的，问我比翻笔记快。";

/** 预设问答：命中关键词时返回高质量回答，未命中则回落到「知识库检索式回答」 */
export const AGENT_PRESETS: AgentPreset[] = [
  {
    markers: ["redis", "击穿"],
    question: "我之前是怎么解决 Redis 缓存击穿的？",
    answer: `你在 **2026-08-07** 遇到过一模一样的问题，并且写下了完整复盘。

## 你当时的处理

1. **先止血**：手动把热点 Key 写回缓存，流量立刻回落
2. **再改造**：热点数据改为**逻辑过期 + 异步重建**
3. **加互斥**：重建过程加分布式锁，只放一个请求进数据库
4. **防扩散**：其余 Key 的过期时间加随机尾巴，避免同时过期

## 你当时踩的坑

- 事故持续了 90 秒，期间数据库连接池 100/100 打满
- 缓存命中率指标看起来正常，掩盖了**瞬时**的未命中并发

## 你自己的结论

> 缓存命中率是平均值，会掩盖瞬时的未命中并发；热点数据要按「过期瞬间的并发」来设计。

按这个思路，这次可以直接复用互斥重建方案，不用再加本地缓存兜底。`,
    trace: [
      "解析问题：命中「Redis + 缓存击穿」主题",
      "在经验库检索 → 命中 1 条：热点 Key 过期要用互斥重建（e-redis-penetration）",
      "在日记库检索 → 命中 1 篇：Redis 缓存击穿把线上打挂的一次复盘（d-002）",
      "在知识库检索 → 命中 3 条：缓存击穿 / Redis 分布式锁 / 缓存雪崩与过期时间打散",
      "按时间排序后，用最近一次（2026-08-07）的经验组织回答",
    ],
    refs: [
      { kind: "experience", id: "e-redis-penetration", title: "热点 Key 过期要用互斥重建", reason: "直接命中你处理过的同类问题" },
      { kind: "diary", id: "d-002", title: "Redis 缓存击穿把线上打挂的一次复盘", reason: "包含完整时间线与止血过程" },
      { kind: "knowledge", id: "k-redis-penetration", title: "缓存击穿", reason: "问题定义与三种解法" },
      { kind: "knowledge", id: "k-redis-lock", title: "Redis 分布式锁", reason: "互斥重建的实现要点" },
    ],
  },
  {
    markers: ["spring ai"],
    question: "我在 Spring AI 上踩过哪些坑？",
    answer: `你的 Spring AI 相关记录集中在 **2026-08-04**，一共沉淀了 2 条经验、4 条知识。

## 三个坑

| 坑 | 现象 | 你的解法 |
| --- | --- | --- |
| 工具不被调用 | 模型不主动查资料，回答全靠猜 | 工具描述里写清「什么场景下用」，而不是只写参数 |
| 返回值过大 | 一次塞回全表，token 爆炸、变慢 | 返回值只留 Top3 片段并带来源标识 |
| Advisor 顺序错误 | 接入 RAG 后模型像没看到资料 | 调整顺序为「记忆 → 检索 → 日志」，并打印最终 prompt |

## 最关键的一条

> 和大模型协作，本质是把「说明书」写清楚 —— 触发场景、返回值体量、切面顺序，任何一项含糊，模型都会用自己的方式猜。

需要的话我可以把这套结论整理成一份「Spring AI 接入检查清单」。`,
    trace: [
      "解析问题：命中「Spring AI」主题",
      "在经验库检索 → 命中 2 条（e-spring-ai-tool、e-spring-ai-advisor）",
      "在知识库检索 → 命中 4 条：Spring AI 框架概览 / Tool Calling / Advisor / PromptTemplate",
      "同主题经验合并为「坑 → 现象 → 解法」表格输出",
    ],
    refs: [
      { kind: "experience", id: "e-spring-ai-tool", title: "大模型工具调用：描述和返回值决定成败", reason: "工具不被调用与返回值过大的完整解法" },
      { kind: "experience", id: "e-spring-ai-advisor", title: "Advisor 顺序错误会互相覆盖上下文", reason: "RAG 失效的根因" },
      { kind: "diary", id: "d-001", title: "用 Spring AI 把面试资料接进知识库", reason: "原始记录" },
      { kind: "knowledge", id: "k-tool-calling", title: "Tool Calling 与工具描述设计", reason: "已沉淀的知识条目" },
    ],
  },
  {
    markers: ["mysql"],
    question: "总结一下我在 MySQL 索引优化上的经验。",
    answer: `你在 MySQL 上记录过 **1 次完整排查**（2026-08-11），沉淀了 2 条知识。

## 你的排查套路

1. **先看执行计划**：\`EXPLAIN\` 关注 type / key / rows / Extra
2. **判断索引是否可用**：函数包裹列、隐式类型转换、最左前缀断裂、范围查询后续列失效
3. **优先改写 SQL**：把 \`DATE(create_time) = 'x'\` 改成范围条件，索引立刻生效（12s → 80ms）
4. **最后才考虑加索引**

## 你写下的结论

> 慢查询的第一步永远是看执行计划；加索引是最后手段，先想办法让 SQL「可被索引使用」。

目前这条经验只覆盖了单表查询，建议后续补一条「多表 JOIN 的驱动表选择」。`,
    trace: [
      "解析问题：命中「MySQL + 索引优化」主题",
      "在经验库检索 → 命中 1 条：SQL 慢先看 EXPLAIN（e-mysql-index）",
      "在知识库检索 → 命中 2 条：索引失效常见场景 / EXPLAIN 执行计划解读",
      "识别知识缺口 → 追加一条「建议补充」",
    ],
    refs: [
      { kind: "experience", id: "e-mysql-index", title: "SQL 慢先看 EXPLAIN，再决定加不加索引", reason: "你沉淀的排查方法论" },
      { kind: "knowledge", id: "k-mysql-index", title: "MySQL 索引失效的常见场景", reason: "根因清单" },
      { kind: "knowledge", id: "k-mysql-explain", title: "EXPLAIN 执行计划解读", reason: "排查工具用法" },
    ],
  },
];

/** 通用兜底回答的引导语（当问题没有命中预设、由知识库检索生成时使用） */
export const AGENT_FALLBACK_HEAD =
  "我在你的知识库里检索了一遍，下面是相关度最高的记录：";

export const AGENT_FOOTER =
  "如果你想把这个结论沉淀成知识条目，可以直接去「知识库」新建，或在日记详情页点击「沉淀知识」。";
