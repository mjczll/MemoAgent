import type { DomainName } from "@/types";

export interface DomainMeta {
  name: DomainName;
  /** 领域简介，用于首页/图谱的领域卡片 */
  desc: string;
  /** 该领域下的主题关键词，用于 Mock AI 做主题识别 */
  keywords: string[];
}

/** 左侧栏「我的领域」与图谱领域筛选的统一定义 */
export const DOMAINS: DomainMeta[] = [
  {
    name: "Java",
    desc: "语言基础、并发、JVM 与性能调优",
    keywords: ["java", "jvm", "gc", "线程池", "并发", "虚拟机", "内存溢出"],
  },
  {
    name: "Spring",
    desc: "Spring Boot 配置、事务与工程结构",
    keywords: ["spring", "spring boot", "事务", "ioc", "aop", "profile", "配置"],
  },
  {
    name: "Redis",
    desc: "缓存策略、击穿雪崩与分布式锁",
    keywords: ["redis", "缓存", "击穿", "雪崩", "穿透", "分布式锁", "lua", "过期"],
  },
  {
    name: "MySQL",
    desc: "索引、执行计划与慢查询优化",
    keywords: ["mysql", "索引", "慢查询", "explain", "sql", "执行计划", "联合索引"],
  },
  {
    name: "Linux",
    desc: "运维排查、日志与磁盘问题定位",
    keywords: ["linux", "服务器", "日志", "磁盘", "inode", "句柄", "部署", "shell"],
  },
  {
    name: "AI Agent",
    desc: "Spring AI、Tool Calling、RAG 与流式交互",
    keywords: [
      "ai",
      "agent",
      "spring ai",
      "llm",
      "大模型",
      "tool calling",
      "function calling",
      "rag",
      "提示词",
      "prompt",
      "embedding",
      "向量",
      "sse",
      "流式",
    ],
  },
  {
    name: "项目开发",
    desc: "接口设计、知识管理与协作习惯",
    keywords: ["接口", "规范", "api", "重构", "复盘", "方法", "笔记", "知识管理"],
  },
];

export const DOMAIN_NAMES: DomainName[] = DOMAINS.map((d) => d.name);
