/**
 * MemoAgent 前端原型的全局类型定义。
 * 所有 Mock 数据、Store、组件都严格基于这些类型，避免 any 与隐式结构。
 */

/** 领域（左侧栏"我的领域"） */
export type DomainName =
  | "Java"
  | "Spring"
  | "Redis"
  | "MySQL"
  | "Linux"
  | "AI Agent"
  | "项目开发";

/** 内容可见性：私有 / 已公开到探索知识 */
export type Visibility = "private" | "public";

/** 日记类型名称。由已有日记聚合而来，没有对应日记则不存在。 */
export type DiaryKind = string;

export interface DiaryKindItem {
  id: string;
  name: DiaryKind;
  sortOrder: number;
  isDefault: boolean;
  diaryCount: number;
}

/** 掌握程度 */
export type Mastery = "已掌握" | "部分掌握" | "待补充";

/** 采访步骤定义 */
export interface InterviewStep {
  id: string;
  /** 步骤名，如「发生了什么」 */
  label: string;
  /** 该步骤希望得到的信息 */
  hint: string;
}

/** 采访会话消息 */
export interface ChatMessage {
  id: string;
  role: "user" | "ai";
  text: string;
  at: string;
  /** 是否处于"思考中"骨架态 */
  pending?: boolean;
  /** 特殊卡片：采访完成后出现的「整理成日记」 */
  action?: "generate-diary";
}

/** 历史采访会话（侧栏"最近会话"列表 + 切换/恢复） */
export interface InterviewSession {
  id: string;
  /** 第一步回答的简短概括，用于列表标题 */
  title: string;
  /** 主题标签 */
  topic: string;
  /** 已完成步骤数 */
  stepIndex: number;
  /** 草稿是否已保存 */
  finished: boolean;
  updatedAt: string;
  /** 完整快照，用于切换回该会话 */
  snapshot: {
    messages: ChatMessage[];
    answers: string[];
    draft: DiaryDraft | null;
  };
}

/** 日记 */
export interface Diary {
  id: string;
  title: string;
  /** ISO 日期 YYYY-MM-DD */
  date: string;
  kind: DiaryKind;
  kindId?: string;
  tags: string[];
  /** 一句话摘要 */
  summary: string;
  /** Markdown 正文 */
  content: string;
  visibility: Visibility;
  experienceIds: string[];
  knowledgeIds: string[];
  experienceCount?: number;
  knowledgeCount?: number;
  /** 来源：采访生成 / 手动记录 */
  origin: "interview" | "manual";
  createdAt: string;
}

/** 从日记中提炼出的经验 */
export interface Experience {
  id: string;
  title: string;
  problem: string;
  cause: string;
  solution: string;
  lesson: string;
  tags: string[];
  domain: DomainName | string;
  /** 来源日记 */
  diaryId: string;
  knowledgeIds: string[];
  visibility: Visibility;
  createdAt: string;
}

/** 知识条目 */
export interface Knowledge {
  id: string;
  title: string;
  /** 分类，用于知识库分组，如「缓存」「索引优化」 */
  category: string;
  domain: DomainName | string;
  tags: string[];
  summary: string;
  /** Markdown 正文 */
  content: string;
  /** 相关知识（用于知识图谱的 knowledge-knowledge 边） */
  relatedIds: string[];
  /** 来源经验 */
  sourceExperienceIds: string[];
  mastery: Mastery;
  visibility: Visibility;
  updatedAt: string;
}

/** 日记草稿（Interview 产出的待确认稿） */
export interface DiaryDraft {
  id: string;
  title: string;
  date: string;
  kind: DiaryKind;
  tags: string[];
  summary: string;
  content: string;
  /** AI 结构化提炼 */
  extraction: {
    problem: string;
    cause: string;
    solution: string;
    lesson: string;
  };
  /** AI 建议关联的知识标题（可能命中已有知识，也可能是新知识） */
  suggestedKnowledge: Array<{ title: string; existingId?: string; reason: string }>;
  /** 草稿是否已保存进日记库 */
  saved: boolean;
  /** 保存后生成的日记 id */
  savedDiaryId?: string;
}

/** 知识图谱节点类型 */
export type GraphNodeType = "diary" | "experience" | "knowledge" | "domain";

export interface GraphNode {
  id: string;
  label: string;
  type: GraphNodeType;
  domain: string;
  /** 关联的实体 id（domain 节点为自身名称） */
  refId: string;
  visibility: Visibility;
  /** 权重用于节点大小 */
  weight: number;
  /** 最近更新时间，用于详情面板 */
  updatedAt?: string;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label: string;
}

export interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** Agent 回答中的引用卡片条目 */
export interface AgentRef {
  kind: "diary" | "experience" | "knowledge";
  id: string;
  title: string;
  reason: string;
}

/** Agent 对话消息 */
export interface AgentMessage {
  id: string;
  role: "user" | "ai";
  text: string;
  at: string;
  /** 折叠展示的推理/检索过程 */
  trace?: string[];
  refs?: AgentRef[];
  pending?: boolean;
}

export interface AgentThread {
  id: string;
  title: string;
  updatedAt: string;
}

/** 探索知识中的公开内容 */
export interface PublicNote {
  id: string;
  title: string;
  author: string;
  domain: string;
  tags: string[];
  summary: string;
  content: string;
  /** 本地来源实体（用于"来自我的知识库"） */
  localSource: { kind: "diary" | "experience" | "knowledge"; id: string };
  likes: number;
  collected?: boolean;
  publishedAt: string;
}

export interface ToastItem {
  id: string;
  text: string;
  kind: "info" | "ok" | "warn" | "error";
}

export type ThemeName = "dark";
