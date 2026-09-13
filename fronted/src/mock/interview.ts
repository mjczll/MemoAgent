import type { DiaryKind, InterviewStep } from "@/types";

/** 采访五步法：AI 像记者一样按顺序把一件事问清楚 */
export const INTERVIEW_STEPS: InterviewStep[] = [
  { id: "s1", label: "发生了什么", hint: "先讲事实：时间、场景、你做了什么" },
  { id: "s2", label: "为什么重要", hint: "它影响了你什么：进度、质量还是心情" },
  { id: "s3", label: "遇到了什么问题", hint: "卡点在哪，报错或现象是什么" },
  { id: "s4", label: "怎么解决的", hint: "试过哪些方案，最后用了哪个" },
  { id: "s5", label: "学到了什么", hint: "下次再遇到类似情况你会怎么做" },
];

export const INTERVIEW_OPENING =
  "今天有什么事情让你印象比较深？随便说，像聊天一样就行 —— 我按顺序问，你按顺序答，最后我帮你整理成日记。";

/** 收尾语：由 store 注入主题信息后展示 */
export const INTERVIEW_CLOSING_PREFIX = "我已经基本了解了。";
export const INTERVIEW_CLOSING_SUFFIX =
  "要我把这次聊的内容整理成今天的日记吗？整理完你还可以继续编辑。";

/** 每一步的追问模板（Mock AI 的"追问"语气，按步数轮换） */
export const FOLLOW_UP_TEMPLATES: Array<{ react: string; ask: string }> = [
  {
    react: "记下来了 —— {echo}。",
    ask: "接着想一层：为什么这件事对你重要？它影响到了什么？",
  },
  {
    react: "嗯，{echo}，这一点很关键。",
    ask: "过程中最大的卡点是什么？出现了什么现象，或者报了什么错？",
  },
  {
    react: "明白，卡在「{echo}」这里。",
    ask: "那你最后是怎么解决的？试过哪些方案，为什么不选其他的？",
  },
  {
    react: "好，解决方案清楚了。",
    ask: "回头看，你从这件事里学到了什么？下次遇到类似情况你会怎么做？",
  },
];

export interface ScenarioPreset {
  key: string;
  label: string;
  domain: string;
  kind: DiaryKind;
  tags: string[];
  /** 命中该场景的关键词（小写） */
  keywords: string[];
  summary: string;
  /** 主题相关的一段背景描述，用于日记正文开头 */
  background: string;
  extraction: {
    problem: string;
    cause: string;
    solution: string;
    lesson: string;
  };
  knowledge: Array<{ title: string; existingId?: string; reason: string }>;
}

/**
 * 演示场景预设：当用户采访内容命中这些主题时，Mock AI 会给出更具体的结构化提炼。
 * 未命中任何场景时，走通用提炼（直接使用用户回答）。
 */
export const SCENARIOS: ScenarioPreset[] = [
  {
    key: "spring-ai",
    label: "Spring AI 踩坑",
    domain: "AI Agent",
    kind: "技术",
    tags: ["Spring AI", "AI Agent", "踩坑"],
    keywords: ["spring ai", "tool calling", "function calling", "advisor", "prompt", "提示词", "大模型", "llm", "agent"],
    summary:
      "接 Spring AI 时踩的几个坑：工具描述写得含糊导致模型不调用、返回值过大撑爆上下文、Advisor 顺序错乱让检索结果被记忆覆盖。",
    background:
      "最近在项目里接入 Spring AI，想把个人的历史资料接进对话，让模型在需要的时候自己去查。",
    extraction: {
      problem:
        "模型该调用检索工具的时候不调用，偶尔调用了又把整段原文塞回来，回答里夹着无关内容；接入 RAG 后还会忽略检索到的资料。",
      cause:
        "工具描述只写了参数没有写「什么场景下用」，模型无法判断触发时机；工具返回值没有裁剪，超出上下文预算；Advisor 链路里记忆排在检索之后，把检索片段覆盖掉了。",
      solution:
        "重写工具描述，明确触发场景与参数取值；返回值只保留 Top3 片段并带来源标识；调整 Advisor 顺序为「记忆 → 检索 → 日志」，并打印最终 prompt 以便验证。",
      lesson:
        "和大模型协作，本质是把「说明书」写清楚：工具的触发场景、返回值的体量、切面的顺序，任何一项含糊，模型都会用自己的方式猜。",
    },
    knowledge: [
      {
        title: "Tool Calling 与工具描述设计",
        existingId: "k-tool-calling",
        reason: "本次踩坑的核心是工具描述与返回值设计",
      },
      {
        title: "Spring AI Advisor 机制",
        existingId: "k-advisor",
        reason: "Advisor 顺序导致检索结果被覆盖",
      },
      {
        title: "PromptTemplate 与提示词结构",
        existingId: "k-prompt-template",
        reason: "提示词需要四段式结构与占位模板",
      },
      {
        title: "SSE 流式响应与服务端 Flush",
        existingId: "k-sse-stream",
        reason: "排查过程中涉及流式返回的逐段验证",
      },
    ],
  },
  {
    key: "redis",
    label: "Redis 缓存问题",
    domain: "Redis",
    kind: "复盘",
    tags: ["Redis", "缓存", "线上问题"],
    keywords: ["redis", "缓存", "击穿", "雪崩", "穿透", "分布式锁", "热点 key"],
    summary:
      "Redis 缓存相关的排查：热点 Key 过期瞬间击穿数据库，需要互斥重建 + 过期时间打散。",
    background: "线上出现与 Redis 缓存相关的性能问题，需要复盘定位并给出改进方案。",
    extraction: {
      problem: "热点数据在缓存过期瞬间有大量请求直接打到数据库，接口响应时间飙升。",
      cause: "所有请求共享同一个缓存 Key，过期后同时未命中，重建过程没有互斥与异步机制。",
      solution: "热点数据改逻辑过期 + 异步重建，重建加分布式锁只放一个请求进库，其余 Key 过期时间加随机尾巴。",
      lesson: "缓存命中率是平均值，会掩盖瞬时的未命中并发；热点数据要按过期瞬间的并发来设计。",
    },
    knowledge: [
      {
        title: "缓存击穿",
        existingId: "k-redis-penetration",
        reason: "本次问题的直接原因",
      },
      {
        title: "Redis 分布式锁",
        existingId: "k-redis-lock",
        reason: "互斥重建用到的技术手段",
      },
      {
        title: "缓存雪崩与过期时间打散",
        existingId: "k-redis-avalanche",
        reason: "同一类问题的预防措施",
      },
    ],
  },
  {
    key: "mysql",
    label: "MySQL 查询优化",
    domain: "MySQL",
    kind: "技术",
    tags: ["MySQL", "索引", "慢查询"],
    keywords: ["mysql", "索引", "慢查询", "explain", "sql", "执行计划", "全表扫描"],
    summary: "MySQL 慢查询排查：先看执行计划，再改写 SQL 让索引可用。",
    background: "数据库查询性能不达标，需要定位扫描行数与索引使用情况。",
    extraction: {
      problem: "接口响应时间远超预期，SQL 执行计划显示全表扫描，扫描行数达百万级。",
      cause: "查询条件对索引列使用了函数或隐式类型转换，导致索引无法被使用。",
      solution: "把条件改写成范围查询，避免在索引列上做运算；用 EXPLAIN 确认 type 与 key 生效。",
      lesson: "慢查询第一步永远是看执行计划；先让 SQL 可被索引使用，再考虑加索引。",
    },
    knowledge: [
      { title: "MySQL 索引失效的常见场景", existingId: "k-mysql-index", reason: "问题根因" },
      { title: "EXPLAIN 执行计划解读", existingId: "k-mysql-explain", reason: "排查方法" },
    ],
  },
  {
    key: "rag",
    label: "RAG 检索效果",
    domain: "AI Agent",
    kind: "技术",
    tags: ["RAG", "检索", "AI Agent"],
    keywords: ["rag", "检索", "召回", "向量", "embedding", "知识库检索"],
    summary: "RAG 答非所问的根因多数在召回侧：切片策略、相似度阈值与 TopK。",
    background: "知识库问答效果不理想，需要判断问题出在检索还是生成。",
    extraction: {
      problem: "提问与回答明显不相关，模型回答了另一个主题的内容。",
      cause: "切片粒度不合理把完整逻辑切断，相似度阈值过松导致召回大量噪声。",
      solution: "按语义段落切片并保留标题路径，提高相似度阈值、降低 TopK，召回为空时明确回答「没有找到相关资料」。",
      lesson: "RAG 的效果上限由召回决定；排查顺序是先看召回原文，再看提示词，最后才是模型。",
    },
    knowledge: [
      { title: "RAG 检索增强生成", existingId: "k-rag-basics", reason: "问题所属技术主题" },
      { title: "向量数据库与 PGVector", existingId: "k-vector-db", reason: "召回参数配置" },
    ],
  },
];

/** 各场景的示例开场（首页快捷入口用） */
export const INTERVIEW_EXAMPLES: Array<{ title: string; text: string; scenarioKey: string }> = [
  {
    title: "今天接了 Spring AI，踩了几个坑",
    text: "今天在项目里接 Spring AI，本来想让它调用我自己的历史资料，结果工具一直不被调用，模型还老把整段资料塞回来，折腾了一下午。",
    scenarioKey: "spring-ai",
  },
  {
    title: "线上缓存又出问题了",
    text: "早上监控报警，某个热点商品的缓存过期了，一瞬间好多请求直接打到数据库，接口全超时。",
    scenarioKey: "redis",
  },
  {
    title: "一个 SQL 跑了 12 秒",
    text: "报表接口特别慢，查了一下发现 SQL 走了全表扫描，原因是 where 里对时间字段用了函数。",
    scenarioKey: "mysql",
  },
  {
    title: "知识库问答开始胡说",
    text: "知识库问答的效果越来越差，问 A 它答 B，怀疑是检索出来的资料就不对。",
    scenarioKey: "rag",
  },
];
