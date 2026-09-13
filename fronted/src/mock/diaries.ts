import type { Diary } from "@/types";

/** 日记库 Mock 数据（12 篇），内容与经验、知识、图谱互相引用 */
export const SEED_DIARIES: Diary[] = [
  {
    id: "d-001",
    title: "用 Spring AI 把面试资料接进知识库",
    date: "2026-08-04",
    kind: "技术",
    tags: ["Spring AI", "Tool Calling", "知识库"],
    summary:
      "第一次在 Spring Boot 里跑通大模型 + 工具调用：让模型自己决定什么时候去查我的历史资料。",
    content: `## 今天做了什么

把之前攒的面试资料整理成 Markdown，写了一个 Spring AI 的 ChatClient，并注册了一个"搜索历史记录"的工具。

## 过程

一开始把所有资料一次性塞进 prompt，结果 3 万多 token，又慢又贵。改成 [[Tool Calling 与工具描述设计]] 之后，模型只在需要时才调用检索工具。

## 关键收获

- 工具的描述就是"说明书"，写清楚**什么场景下用**比写清楚参数更重要
- 返回值不要直接扔整张表回去，先裁剪成 Top3 片段

## 遗留

Advisor 的顺序还没调对，记忆和检索偶尔互相盖掉。`,
    visibility: "private",
    experienceIds: ["e-spring-ai-tool", "e-spring-ai-advisor"],
    knowledgeIds: ["k-spring-ai", "k-tool-calling", "k-prompt-template"],
    origin: "interview",
    createdAt: "2026-08-04 21:40",
  },
  {
    id: "d-002",
    title: "Redis 缓存击穿把线上打挂的一次复盘",
    date: "2026-08-07",
    kind: "复盘",
    tags: ["Redis", "缓存击穿", "线上事故"],
    summary:
      "一个 20 万 QPS 的热点 Key 到期，数据库连接池瞬间打满，接口全超时，持续了 90 秒。",
    content: `## 事故现场

21:07 监控报警：订单详情接口 P99 从 40ms 飙到 8s，数据库连接数 100/100 打满。

## 时间线

- 21:07 热点商品缓存到期，缓存层瞬时未命中
- 21:07-21:09 约 6 万请求直接打到 MySQL
- 21:09 手动把该 Key 写回缓存，流量回落

## 原因

这是典型的 [[缓存击穿]]：单个热点 Key 过期瞬间无人挡在前面。

## 改了什么

1. 热点数据改成逻辑过期 + 异步重建
2. 重建过程加 [[Redis 分布式锁]]，只放一个请求进数据库
3. 顺手给其他 Key 的过期时间加了随机尾巴，防 [[缓存雪崩与过期时间打散]]

> 教训：缓存命中率这个指标会骗人，要看的是**瞬时的**未命中并发。`,
    visibility: "private",
    experienceIds: ["e-redis-penetration"],
    knowledgeIds: ["k-redis-penetration", "k-redis-lock", "k-redis-avalanche"],
    origin: "interview",
    createdAt: "2026-08-07 22:15",
  },
  {
    id: "d-003",
    title: "MySQL 慢查询：函数包住索引列引发的血案",
    date: "2026-08-11",
    kind: "技术",
    tags: ["MySQL", "索引失效", "慢查询"],
    summary: "一个走了全表扫描的报表 SQL：因为 where 里对时间列用了 DATE() 函数。",
    content: `## 现象

报表接口 12 秒返回，\`EXPLAIN\` 显示 type=ALL，扫描 240 万行。

## 原因

\`\`\`sql
WHERE DATE(create_time) = '2026-08-10'
\`\`\`

对索引列做函数运算，[[MySQL 索引失效的常见场景]] 里的第一条。

## 改法

改成范围条件，让索引可用：

\`\`\`sql
WHERE create_time >= '2026-08-10 00:00:00'
  AND create_time <  '2026-08-11 00:00:00'
\`\`\`

耗时从 12s 降到 80ms。

## 学到

先看 [[EXPLAIN 执行计划解读]] 再决定加不加索引，别一慢就建索引。`,
    visibility: "private",
    experienceIds: ["e-mysql-index"],
    knowledgeIds: ["k-mysql-index", "k-mysql-explain"],
    origin: "manual",
    createdAt: "2026-08-11 16:05",
  },
  {
    id: "d-004",
    title: "知识库上传接口 500：磁盘满了，inode 也满了",
    date: "2026-08-13",
    kind: "技术",
    tags: ["Linux", "磁盘", "运维"],
    summary: "上传接口忽然全部 500，不是代码问题：日志把小文件写满，inode 先于磁盘空间耗尽。",
    content: `## 排查过程

1. \`tail -f app.log\` 只看到 IOException: No space left on device
2. \`df -h\` 显示还有 3G 剩余空间 —— 说明不是空间问题
3. \`df -i\` 显示 inode 使用率 **100%**
4. \`du -sh /var/log/app/*\` 定位到某个按分钟切分的日志目录，积了 90 万个小文件

## 处理

清理历史小文件 + 调整切割策略，从按分钟改成按小时，并加保留 7 天的清理任务。写进 [[Linux 磁盘与 inode 排查]]。

## 顺带学到的

容器被 OOM Kill 时应用日志是空的，一定要看 \`dmesg\` / \`journalctl\`，见 [[Linux 日志排查套路]]。`,
    visibility: "private",
    experienceIds: ["e-linux-fd"],
    knowledgeIds: ["k-linux-disk", "k-linux-log"],
    origin: "interview",
    createdAt: "2026-08-13 19:22",
  },
  {
    id: "d-005",
    title: "SSE 流式回答一直转圈，最后发现是 Nginx 缓冲",
    date: "2026-08-16",
    kind: "技术",
    tags: ["SSE", "流式", "Nginx"],
    summary: "后端明明在流式输出，前端要等 8 秒才一次性显示全部内容。",
    content: `## 现象

Postman 里能看到逐段输出，浏览器里却是"转圈 8 秒 + 一次性吐完"。

## 定位

逐层排除：

- 后端：\`Flux\` 每段写完后 flush 了 ✅
- Nginx：\`proxy_buffering\` 默认 on ❌
- 前端：用了 \`await res.text()\` 而不是流式读取 ❌

## 结论

两个问题叠在一起。修掉 Nginx 缓冲 + 改成 \`getReader()\` 增量解码后就正常了，详见 [[SSE 流式响应与服务端 Flush]]。

## 反思

流式接口的调试要**按链路逐段验证**，不能只看两端。`,
    visibility: "private",
    experienceIds: ["e-sse-flush"],
    knowledgeIds: ["k-sse-stream"],
    origin: "interview",
    createdAt: "2026-08-16 20:10",
  },
  {
    id: "d-006",
    title: "线程池队列没设容量，任务堆了 40 万条",
    date: "2026-08-19",
    kind: "技术",
    tags: ["Java", "线程池", "并发"],
    summary: "用默认无界队列，任务只排队不拒绝，最后 OOM。",
    content: `## 经过

导出任务积压告警，堆内存一路涨到 90%，最后 OOM。查下来线程池用了 \`Executors.newFixedThreadPool\`，队列是无界的。

## 原因

无界队列 + 默认拒绝策略 = 任务永远不拒绝，只是把内存吃光。核心线程数也只有 4，处理速度远低于入队速度。

## 修正

- 换成 \`ThreadPoolExecutor\`，队列容量 2000
- 核心线程数按 IO 密集算，见 [[线程池参数与队列选择]]
- 拒绝策略改成自定义：落库 + 打日志 + 返回降级
- 顺手用 \`jstat\` 观察了 GC，补齐 [[JVM 内存与 GC 排查]]

> 记一句：**无界队列是最危险的默认值**。`,
    visibility: "private",
    experienceIds: ["e-thread-pool"],
    knowledgeIds: ["k-java-concurrency", "k-jvm-gc"],
    origin: "interview",
    createdAt: "2026-08-19 18:45",
  },
  {
    id: "d-007",
    title: "Spring 事务没回滚：同类方法调用不走代理",
    date: "2026-08-22",
    kind: "技术",
    tags: ["Spring", "事务", "AOP"],
    summary: "子方法加了 @Transactional 却依然写入了脏数据，因为它是被 this 调用的。",
    content: `## 现象

订单创建失败后，库存扣减没有回滚。

## 定位

\`orderService.create()\` 内部直接调用了本类的 \`deductStock()\`，而事务注解在 \`deductStock()\` 上。

同类内部调用不经过 Spring 代理，注解形同虚设 —— [[Spring 事务失效与自调用]]。

## 修复

把扣库存拆成独立的 \`StockService\`，并显式写 \`rollbackFor = Exception.class\`。

## 补充

顺便确认了异常被 catch 掉也不会回滚，所以关键路径都加了"事务已回滚"的日志。`,
    visibility: "private",
    experienceIds: ["e-spring-tx"],
    knowledgeIds: ["k-spring-transaction"],
    origin: "manual",
    createdAt: "2026-08-22 11:30",
  },
  {
    id: "d-008",
    title: "第二次设计 KDS：先画领域模型再写代码",
    date: "2026-08-25",
    kind: "项目",
    tags: ["项目", "领域建模", "设计"],
    summary: "上一版边写边改，接口改了 5 次；这次先花半天画模型，接口一次成型。",
    content: `## 上次的问题

直接照着 UI 写接口，字段跟着页面走，后期几乎每个页面改动都要改接口。

## 这次的做法

1. 先把核心概念抽出来：订单、门店、菜品、出餐批次
2. 明确每个概念的**状态流转**，再映射到接口
3. UI 只做视图适配，不让页面字段污染模型

## 结果

半天建模，接口定义一次通过评审，联调时间比上次少了一半。这条写进了 [[REST 接口设计与统一响应]] 的实践部分。`,
    visibility: "private",
    experienceIds: [],
    knowledgeIds: ["k-api-design"],
    origin: "manual",
    createdAt: "2026-08-25 21:00",
  },
  {
    id: "d-009",
    title: "复盘：我为什么总是低估联调时间",
    date: "2026-08-28",
    kind: "复盘",
    tags: ["复盘", "时间管理", "协作"],
    summary: "三次迭代联调都超期，原因高度一致：接口约定没落到文档，全靠口头同步。",
    content: `## 统计

最近三次迭代，联调超期分别是 2 天、1.5 天、3 天。

## 原因

- 接口字段变更只在群里说一句，前端没同步
- Mock 数据字段和后端字段名不一致
- 没有明确的"接口冻结时间"

## 改进

- 接口先对齐字段名与空值语义，写进文档再开工
- 前端用 Mock 平铺开发，字段以后端契约为准
- 冻结时间写进迭代计划，冻结后变更必须走评审

> 联调不是"写完了对接一下"，而是**约定管理**。`,
    visibility: "private",
    experienceIds: [],
    knowledgeIds: [],
    origin: "manual",
    createdAt: "2026-08-28 22:30",
  },
  {
    id: "d-010",
    title: "RAG 召回不准，不是模型的问题",
    date: "2026-09-01",
    kind: "技术",
    tags: ["RAG", "检索", "AI Agent"],
    summary: "模型答非所问的根因在召回：切片把一段完整逻辑切成两半，谁都没法用。",
    content: `## 现象

问"分布式锁怎么释放"，模型回答的是缓存过期策略。

## 定位方法

先把召回的原文打出来看 —— 命中片段里根本没有讲到解锁。

## 两个问题

1. 切片按固定 500 字硬切，把"加锁 + 解锁"切到两个 chunk
2. 相似度阈值设得太松，TopK=8 带进来一堆噪声

## 改进

- 改成按语义段落切片，并保留标题路径
- 阈值提高到 0.75，TopK 降到 4
- 命中为 0 时明确回答"没有找到相关资料"，而不是硬编

详见 [[RAG 检索增强生成]] 和 [[向量数据库与 PGVector]]。`,
    visibility: "public",
    experienceIds: ["e-rag-recall"],
    knowledgeIds: ["k-rag-basics", "k-vector-db"],
    origin: "interview",
    createdAt: "2026-09-01 20:50",
  },
  {
    id: "d-011",
    title: "读《卡片笔记写作法》：从摘抄到连接",
    date: "2026-09-05",
    kind: "学习",
    tags: ["读书", "知识管理", "方法"],
    summary: "摘抄是记忆的假象，真正的理解发生在「用自己的话重写，并连到已有笔记」的那一刻。",
    content: `## 三个让我停下来的点

1. **原子化**：一张卡片只放一个想法，粒度小才好复用
2. **用自己的话写**：复制原文等于把理解外包给了未来的自己
3. **强制链接**：每张新卡片至少连一个旧概念，否则它会被遗忘

## 我的实践调整

- 日记只写"今天发生了什么"，抽象结论单独沉到知识条目
- 每写完一条知识，强制补 1-3 个 \`[[]]\` 链接

对应知识：[[Markdown 双链与卡片笔记法]]。`,
    visibility: "public",
    experienceIds: ["e-note-method"],
    knowledgeIds: ["k-obsidian-markdown"],
    origin: "manual",
    createdAt: "2026-09-05 22:05",
  },
  {
    id: "d-012",
    title: "给团队写接口规范时的一些取舍",
    date: "2026-09-09",
    kind: "项目",
    tags: ["规范", "API", "协作"],
    summary: "规范不是越全越好：能从既有代码里长出来的约定，才有人愿意遵守。",
    content: `## 写了什么

- 统一响应体 \`{code,message,data}\`，错误码分域
- 列表接口统一分页字段
- 流式接口单独约定事件格式

## 刻意没写的

- 没有强制所有接口都返回同样的包装（文件下载、流式例外）
- 没有定义几十个错误码，只定了分域规则

## 原因

规范的目标是**降低沟通成本**，不是追求形式统一。写太细没人看，写太少没约束，最后选了"规则 + 示例"的形式。沉淀为 [[REST 接口设计与统一响应]]。`,
    visibility: "public",
    experienceIds: ["e-api-design"],
    knowledgeIds: ["k-api-design"],
    origin: "manual",
    createdAt: "2026-09-09 20:00",
  },
];
