import type { Knowledge } from "@/types";

/**
 * 知识库 Mock 数据（20 条）。
 * 知识由「经验」沉淀而来，因此 sourceExperienceIds 指向 experiences.ts 中的经验。
 */
export const SEED_KNOWLEDGE: Knowledge[] = [
  {
    id: "k-spring-ai",
    title: "Spring AI 框架概览",
    category: "框架",
    domain: "AI Agent",
    tags: ["Spring AI", "LLM", "架构"],
    summary:
      "Spring AI 把大模型调用抽象成 ChatClient / ChatModel / EmbeddingModel 三层，配合 Advisor 形成可插拔的调用链。",
    content: `## 一句话理解

Spring AI 是 Spring 生态里的大模型接入层：用**统一的 ChatClient API** 屏蔽不同厂商模型的差异。

## 三层抽象

- \`ChatModel\`：具体厂商实现（OpenAI / Ollama / 通义等）
- \`ChatClient\`：面向业务的流式/同步调用入口，支持 prompt 模板与结构化输出
- \`Advisor\`：调用链上的可插拔切面，做记忆、RAG、日志

## 什么时候值得用

1. 已经在 Spring Boot 体系内，不想引入第二套技术栈
2. 需要把模型调用和现有事务、配置、监控打通

> 结论：Spring AI 的价值不在"调用模型"，而在"让模型调用融入 Spring 的工程习惯"。`,
    relatedIds: ["k-tool-calling", "k-advisor", "k-prompt-template"],
    sourceExperienceIds: ["e-spring-ai-tool", "e-spring-ai-advisor"],
    mastery: "部分掌握",
    visibility: "public",
    updatedAt: "2026-08-21",
  },
  {
    id: "k-tool-calling",
    title: "Tool Calling 与工具描述设计",
    category: "智能体",
    domain: "AI Agent",
    tags: ["Tool Calling", "Function Calling", "Agent"],
    summary:
      "模型只能看到工具的名称、描述和参数 Schema；工具选不对，九成是描述写得不像「给模型看的说明书」。",
    content: `## 调用链路

用户提问 → 模型决定是否调用工具 → 框架反射执行 → 结果回填 → 模型组织答案。

## 描述设计三原则

1. **描述里要有触发场景**，例如"当用户询问历史记录时使用"
2. **参数描述写单位与取值**，不要只写参数名
3. **工具粒度按业务动作切**，不要做"万能工具"

## 常见坑

- 参数用 \`Map<String,Object>\`：模型无法理解结构
- 工具返回值过大：直接把整张表塞回上下文，token 爆炸
- 没有幂等保护：模型重试时重复写库`,
    relatedIds: ["k-spring-ai", "k-prompt-template", "k-sse-stream"],
    sourceExperienceIds: ["e-spring-ai-tool"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-21",
  },
  {
    id: "k-prompt-template",
    title: "PromptTemplate 与提示词结构",
    category: "提示词工程",
    domain: "AI Agent",
    tags: ["Prompt", "PromptTemplate"],
    summary: "把提示词当代码管理：角色 / 输入 / 约束 / 输出格式四段式，用模板占位而不是字符串拼接。",
    content: `## 四段式结构

1. **角色**：你是谁
2. **输入**：已知信息（用分隔符包住，避免注入）
3. **约束**：不能做什么、必须引用什么
4. **输出格式**：Markdown / JSON，字段名写清楚

## 工程化

- 提示词放资源文件，改文案不改代码
- 用 \`{placeholder}\` 占位，禁止 \`"..." + var\` 拼接
- 要求"引用来源"时，让模型输出结构化字段，前端再渲染成链接`,
    relatedIds: ["k-spring-ai", "k-tool-calling"],
    sourceExperienceIds: ["e-spring-ai-tool"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-18",
  },
  {
    id: "k-advisor",
    title: "Spring AI Advisor 机制",
    category: "智能体",
    domain: "AI Agent",
    tags: ["Advisor", "拦截器", "RAG"],
    summary: "Advisor 是模型调用前后的切面，用来串起记忆、知识检索与日志，顺序错了会互相覆盖上下文。",
    content: `## 是什么

\`Advisor\` 类似 Servlet Filter：在请求进入模型前后各插一段逻辑。

## 典型 Advisor

- \`MessageChatMemoryAdvisor\`：注入/写回对话记忆
- \`QuestionAnswerAdvisor\`：先向量检索，再把命中片段拼进 prompt
- 自定义日志 Advisor：记录耗时、token 用量

## 顺序陷阱

记忆 Advisor 必须在 RAG Advisor **之前**，否则检索到的片段会被记忆覆盖，表现为"模型答得不看资料"。`,
    relatedIds: ["k-spring-ai", "k-rag-basics", "k-vector-db"],
    sourceExperienceIds: ["e-spring-ai-advisor"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-21",
  },
  {
    id: "k-rag-basics",
    title: "RAG 检索增强生成",
    category: "检索",
    domain: "AI Agent",
    tags: ["RAG", "检索", "召回"],
    summary: "RAG 的效果上限由召回决定：切片策略、相似度阈值、TopK 任一项配错，模型再强也救不回来。",
    content: `## 流程

文档 → 切片 → 向量化 → 存库 → 提问时向量检索 → 拼接上下文 → 模型作答。

## 决定效果的三件事

1. **切片粒度**：按语义段落切，不要按固定 500 字硬切
2. **相似度阈值**：低于阈值宁可不召回，避免"用错资料编答案"
3. **TopK 与去重**：TopK 太大噪声多，相同文档要去重

## 判断是不是模型的问题

先看召回的原文对不对。召回错了 → 检索问题；召回对了答案错 → 才是模型/提示词问题。`,
    relatedIds: ["k-vector-db", "k-advisor", "k-prompt-template"],
    sourceExperienceIds: ["e-rag-recall"],
    mastery: "部分掌握",
    visibility: "public",
    updatedAt: "2026-09-01",
  },
  {
    id: "k-vector-db",
    title: "向量数据库与 PGVector",
    category: "检索",
    domain: "AI Agent",
    tags: ["PGVector", "Embedding", "索引"],
    summary: "PGVector 适合已有 PostgreSQL 的项目起步；维度、距离度量、IVFFlat 的 lists 参数要一起定。",
    content: `## 关键参数

- **维度**：必须与 embedding 模型一致（如 1536 / 1024）
- **距离度量**：文本多用余弦距离
- **索引**：数据量大时建 IVFFlat / HNSW，\`lists\` 经验值 ≈ \`rows / 1000\`

## 工程建议

- 向量表和业务表放同一个库，减少一次网络往返
- 一定要存原文与元数据，否则召回后无法核对`,
    relatedIds: ["k-rag-basics"],
    sourceExperienceIds: ["e-rag-recall"],
    mastery: "待补充",
    visibility: "private",
    updatedAt: "2026-08-26",
  },
  {
    id: "k-sse-stream",
    title: "SSE 流式响应与服务端 Flush",
    category: "流式",
    domain: "AI Agent",
    tags: ["SSE", "流式", "Flux"],
    summary: "SSE 前端一直转圈，多半是服务端没有 flush、代理缓冲了响应，或前端没按分帧读取。",
    content: `## 排查顺序

1. 服务端：\`Content-Type: text/event-stream\`，每段写完后必须 flush
2. 中间层：Nginx \`proxy_buffering off\`，否则整包返回
3. 前端：用流式 reader 按 \`\\n\\n\` 分帧，不能等整个响应体

## 前端分帧要点

- \`fetch\` + \`response.body.getReader()\`，用 TextDecoder 增量解码
- 处理跨 chunk 被截断的半个事件
- 结束条件以 \`[DONE]\` 或流关闭为准`,
    relatedIds: ["k-spring-ai", "k-api-design"],
    sourceExperienceIds: ["e-sse-flush"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-16",
  },
  {
    id: "k-redis-penetration",
    title: "缓存击穿",
    category: "缓存",
    domain: "Redis",
    tags: ["缓存击穿", "热点 Key", "互斥锁"],
    summary: "单个热点 Key 过期瞬间，大量请求同时打到数据库；解决靠互斥重建 + 逻辑过期。",
    content: `## 现象

某个热点 Key 到期的一瞬间，数据库 QPS 尖刺，接口 P99 飙升，但缓存命中率看起来正常。

## 三种解法

1. **互斥锁**：只让一个请求去加载，其余等待（简单，但要注意锁超时）
2. **逻辑过期**：value 里存过期时间，异步重建，接口永远不阻塞
3. **热点永不过期 + 后台刷新**：适合可接受秒级延迟的数据

## 和雪崩、穿透的区别

- 击穿：**单个**热点 Key 过期
- 雪崩：**大批** Key 同时过期
- 穿透：查**不存在**的数据，缓存永远不命中`,
    relatedIds: ["k-redis-avalanche", "k-redis-lock"],
    sourceExperienceIds: ["e-redis-penetration"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-07",
  },
  {
    id: "k-redis-avalanche",
    title: "缓存雪崩与过期时间打散",
    category: "缓存",
    domain: "Redis",
    tags: ["缓存雪崩", "过期时间", "预热"],
    summary: "大批 Key 同时过期或 Redis 整体不可用，都会引发雪崩；核心是打散过期时间 + 降级兜底。",
    content: `## 预防手段

- 过期时间加随机尾巴：\`base + random(0, 300s)\`
- 缓存预热：发布后按热点顺序预先加载
- 多级缓存：本地缓存挡住第一波
- 熔断降级：Redis 故障时返回旧数据而不是打库`,
    relatedIds: ["k-redis-penetration"],
    sourceExperienceIds: ["e-redis-penetration"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-07",
  },
  {
    id: "k-redis-lock",
    title: "Redis 分布式锁",
    category: "并发",
    domain: "Redis",
    tags: ["分布式锁", "SETNX", "Lua"],
    summary: "加锁用 SET NX PX，解锁必须用 Lua 比对 owner，锁超时要小于业务耗时并考虑续期。",
    content: `## 正确姿势

\`\`\`
SET key value NX PX 30000
\`\`\`

- value 存唯一标识（请求 id / 线程 id），避免解错别人的锁
- 解锁用 Lua：比对 value 再删除，保证原子
- 锁超时 < 业务最大耗时，长任务用看门狗续期

## 不要做的事

- 用 \`setnx\` + \`expire\` 两条命令（非原子）
- 业务抛异常后忘记释放`,
    relatedIds: ["k-redis-penetration"],
    sourceExperienceIds: ["e-redis-penetration"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-07",
  },
  {
    id: "k-mysql-index",
    title: "MySQL 索引失效的常见场景",
    category: "索引优化",
    domain: "MySQL",
    tags: ["索引失效", "联合索引", "最左前缀"],
    summary: "函数包裹列、隐式类型转换、最左前缀断裂、范围查询后的列都用不上索引。",
    content: `## 高频原因

1. 对索引列做函数/运算：\`DATE(create_time) = '2026-08-11'\`
2. 隐式类型转换：字符串列用数字比较
3. 联合索引跳过最左列
4. 范围查询（\`>\`、\`like 'x%'\` 之后）让后续列失效
5. 优化器估算回表成本过高，主动放弃索引

## 处理套路

先 \`EXPLAIN\` 看 type / key / rows，再改写 SQL 让它"可被索引使用"，而不是先加索引。`,
    relatedIds: ["k-mysql-explain"],
    sourceExperienceIds: ["e-mysql-index"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-11",
  },
  {
    id: "k-mysql-explain",
    title: "EXPLAIN 执行计划解读",
    category: "索引优化",
    domain: "MySQL",
    tags: ["EXPLAIN", "执行计划", "type"],
    summary: "重点看 type、key、rows、Extra：出现 Using filesort / Using temporary 就要警惕。",
    content: `## 关注字段

- \`type\`：\`const > eq_ref > ref > range > index > ALL\`
- \`key\`：实际使用的索引，NULL 表示没用上
- \`rows\`：预估扫描行数，与实际差距大说明统计信息过期
- \`Extra\`：\`Using index\` 是好消息，\`Using filesort\` 需优化

## 进阶

- \`EXPLAIN ANALYZE\` 看真实耗时
- \`SHOW INDEX FROM t\` 看索引基数，基数太低不值得建索引`,
    relatedIds: ["k-mysql-index"],
    sourceExperienceIds: ["e-mysql-index"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-11",
  },
  {
    id: "k-jvm-gc",
    title: "JVM 内存与 GC 排查",
    category: "性能",
    domain: "Java",
    tags: ["JVM", "GC", "OOM"],
    summary: "先用 jstat 看 GC 频率和回收效果，再用堆 dump 找对象引用链，别一上来就调参数。",
    content: `## 排查顺序

1. \`jstat -gcutil <pid> 1000\`：观察 GC 频率、老年代增长
2. \`jmap -histo:live\`：看哪些类占内存
3. 堆 dump + MAT：找支配树与 GC Root 引用链
4. 定位到代码后再谈参数

## 常见结论

- 老年代持续增长且 Full GC 后不降 → 内存泄漏
- 频繁 Young GC 且对象晋升快 → 单次批处理数据量太大`,
    relatedIds: ["k-java-concurrency"],
    sourceExperienceIds: ["e-thread-pool"],
    mastery: "待补充",
    visibility: "private",
    updatedAt: "2026-08-19",
  },
  {
    id: "k-java-concurrency",
    title: "线程池参数与队列选择",
    category: "并发",
    domain: "Java",
    tags: ["线程池", "队列", "拒绝策略"],
    summary: "核心线程数看任务类型（CPU/IO），队列必须有界，拒绝策略要显式声明并记录日志。",
    content: `## 参数推导

- CPU 密集：核心数 ≈ 核数 + 1
- IO 密集：核心数 ≈ 核数 × (1 + 等待时间/计算时间)

## 队列选择

| 队列 | 特点 | 适用 |
| --- | --- | --- |
| ArrayBlockingQueue | 有界 | 默认首选 |
| LinkedBlockingQueue | 默认无界，易堆积 | 必须显式设容量 |
| SynchronousQueue | 不排队 | 高吞吐短任务 |

## 拒绝策略

默认 AbortPolicy 会抛异常；生产上更常用自定义策略：落库/打日志 + 降级返回，避免任务静默丢失。`,
    relatedIds: ["k-jvm-gc"],
    sourceExperienceIds: ["e-thread-pool"],
    mastery: "部分掌握",
    visibility: "public",
    updatedAt: "2026-08-19",
  },
  {
    id: "k-spring-transaction",
    title: "Spring 事务失效与自调用",
    category: "事务",
    domain: "Spring",
    tags: ["事务", "AOP", "自调用"],
    summary: "同类内部方法直接调用不走代理，@Transactional 不生效；异常类型不对也不会回滚。",
    content: `## 失效场景

1. **自调用**：\`this.methodB()\` 绕过代理
2. 方法不是 public
3. 异常被 catch 掉，或抛的是检查异常（默认只回滚 RuntimeException）
4. 传播行为配成 \`NOT_SUPPORTED\` / \`REQUIRES_NEW\` 时断开原事务

## 解法

- 自调用：拆成独立 Bean，或注入自身代理
- 明确 \`rollbackFor = Exception.class\`
- 关键路径补日志，确认事务提交/回滚真的发生了`,
    relatedIds: ["k-spring-boot-config"],
    sourceExperienceIds: ["e-spring-tx"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-22",
  },
  {
    id: "k-spring-boot-config",
    title: "Spring Boot 配置与 Profile 隔离",
    category: "配置",
    domain: "Spring",
    tags: ["配置", "Profile", "环境"],
    summary: "环境差异只放在 profile 文件里，敏感配置走环境变量，禁止把本地配置提交上去。",
    content: `## 组织方式

- \`application.yml\`：通用配置
- \`application-dev.yml / prod.yml\`：环境差异
- 敏感值用 \`\${ENV_VAR}\` 占位，不写默认值

## 常见问题

- 本地能跑、线上不行：八成是 profile 没激活
- 配置优先级混乱：记住「命令行 > 环境变量 > profile 文件 > 默认」`,
    relatedIds: ["k-spring-transaction"],
    sourceExperienceIds: [],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-07-30",
  },
  {
    id: "k-linux-disk",
    title: "Linux 磁盘与 inode 排查",
    category: "运维",
    domain: "Linux",
    tags: ["磁盘", "inode", "句柄"],
    summary: "df 看空间、df -i 看 inode、du 逐层定位；删除文件后空间不释放要查被占用句柄。",
    content: `## 三把斧

1. \`df -h\`：整体空间
2. \`df -i\`：inode 是否耗尽（小文件多时很容易满）
3. \`du -sh * | sort -h\`：逐层定位大目录

## 空间没释放

文件被删除但仍被进程占用：\`lsof | grep deleted\`，重启或 truncate 对应 fd 才会真正释放。

## 预防

日志按天切割 + 保留 N 天，务必有清理任务。`,
    relatedIds: ["k-linux-log"],
    sourceExperienceIds: ["e-linux-fd"],
    mastery: "已掌握",
    visibility: "public",
    updatedAt: "2026-08-13",
  },
  {
    id: "k-linux-log",
    title: "Linux 日志排查套路",
    category: "运维",
    domain: "Linux",
    tags: ["日志", "journalctl", "grep"],
    summary: "先看应用日志尾部，再用 journalctl / dmesg 看系统层，最后用进程状态收口。",
    content: `## 套路

1. \`tail -f app.log\`：先看现场
2. \`grep -n "ERROR" app.log | tail -50\`：聚合同类错误
3. \`journalctl -u myservice --since "10 min ago"\`：服务重启/被杀原因
4. \`dmesg | tail\`：OOM Killer、磁盘错误

> 经验：容器里被 OOM Kill 时，应用日志往往什么都没写，一定要看内核日志。`,
    relatedIds: ["k-linux-disk"],
    sourceExperienceIds: ["e-linux-fd"],
    mastery: "部分掌握",
    visibility: "private",
    updatedAt: "2026-08-13",
  },
  {
    id: "k-obsidian-markdown",
    title: "Markdown 双链与卡片笔记法",
    category: "知识管理",
    domain: "项目开发",
    tags: ["Obsidian", "双链", "卡片笔记"],
    summary: "笔记的价值来自连接：原子化拆卡、用自己的话写、每张卡片至少链接一个已有概念。",
    content: `## 核心动作

1. **原子化**：一张卡片只讲一个想法
2. **用自己的话**：摘抄不等于理解
3. **强制链接**：写完必须连到已有卡片，形成网络

## 和 [[WikiLink]] 的关系

\`[[知识标题]]\` 既是引用也是入口，长期积累后反向链接会自然形成知识图谱。`,
    relatedIds: ["k-api-design"],
    sourceExperienceIds: ["e-note-method"],
    mastery: "部分掌握",
    visibility: "public",
    updatedAt: "2026-09-05",
  },
  {
    id: "k-api-design",
    title: "REST 接口设计与统一响应",
    category: "接口设计",
    domain: "项目开发",
    tags: ["API", "统一响应", "错误码"],
    summary: "统一响应体 {code,message,data} + 明确的错误码分层，是前后端协作成本最低的约定。",
    content: `## 统一响应

\`\`\`json
{ "code": 0, "message": "ok", "data": {} }
\`\`\`

- \`code\` 分域：1xxx 参数、2xxx 权限、5xxx 服务端
- HTTP 状态码表达传输层语义，业务码表达业务语义

## 分页与流式

- 列表统一 \`{list, total, page, size}\`
- 流式接口单独约定事件格式，不复用统一响应体`,
    relatedIds: ["k-sse-stream", "k-obsidian-markdown"],
    sourceExperienceIds: ["e-api-design"],
    mastery: "部分掌握",
    visibility: "public",
    updatedAt: "2026-09-09",
  },
];
