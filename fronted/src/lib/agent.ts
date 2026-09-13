import type { AgentRef } from "@/types";
import { AGENT_PRESETS, AGENT_FALLBACK_HEAD, AGENT_FOOTER } from "@/mock/agent";
import { searchLibrary, domainStats, type LibraryData, type LibraryHit } from "./search";
import { extractKeywords } from "./topic";

export interface AgentAnswer {
  text: string;
  trace: string[];
  refs: AgentRef[];
}

const KIND_LABEL: Record<LibraryHit["kind"], string> = {
  experience: "经验",
  diary: "日记",
  knowledge: "知识",
};

const KIND_ICON: Record<LibraryHit["kind"], string> = {
  experience: "◆",
  diary: "▤",
  knowledge: "●",
};

/** 用预设答案回答问题 */
function presetAnswer(question: string): AgentAnswer | null {
  const lower = question.toLowerCase();
  const preset = AGENT_PRESETS.find((p) => p.markers.every((m) => lower.includes(m.toLowerCase())));
  if (!preset) return null;
  return { text: preset.answer, trace: [...preset.trace], refs: preset.refs.map((r) => ({ ...r })) };
}

/** 兜底：基于本地知识库检索拼装回答 */
function libraryAnswer(question: string, data: LibraryData): AgentAnswer {
  const hits = searchLibrary(question, data, 7, 3, true);
  const keywords = extractKeywords(question, 3);

  const trace = [
    `解析问题：提取关键词「${keywords.join("、") || question.slice(0, 12)}」`,
    "在经验库中检索 → 命中 " + hits.filter((h) => h.kind === "experience").length + " 条",
    "在日记库中检索 → 命中 " + hits.filter((h) => h.kind === "diary").length + " 条",
    "在知识库中检索 → 命中 " + hits.filter((h) => h.kind === "knowledge").length + " 条",
    hits.length ? "按相关度合并排序，取前 7 条组织回答" : "命中为 0 → 切换为「引导 + 领域推荐」回答",
  ];

  if (!hits.length) {
    const top = domainStats(data)
      .filter((d) => d.count > 0)
      .sort((a, b) => b.count - a.count)
      .slice(0, 3);

    const lines = [
      `我在你的日记、经验和知识库里都翻了一遍，没有找到和「${question}」直接对应的记录。`,
      "",
    ];
    if (top.length) {
      lines.push(
        `你沉淀最多的领域是 ${top.map((d) => `**${d.name}**（${d.count} 条）`).join("、")}，如果这件事和它们相关，换个说法我可能就能命中。`,
      );
      lines.push("");
    }
    lines.push(
      "可以这样再问我一次：",
      "- 带上技术名或现象，例如「Redis 缓存击穿怎么处理」「索引失效怎么排查」",
      "- 带上时间线索，例如「我上周记的那次线上事故」",
      "",
      "如果这是一件还没记录过的事，去 **采访现场** 讲一遍最快 —— 讲完我能顺手帮你整理成日记、经验卡和知识条目。",
    );

    return {
      text: lines.join("\n"),
      trace: [...trace, "未命中任何本地记录 → 返回引导与领域推荐"],
      refs: [],
    };
  }

  const body = [
    AGENT_FALLBACK_HEAD,
    "",
    ...hits.map(
      (h) =>
        `- ${KIND_ICON[h.kind]} **${h.title}**（${KIND_LABEL[h.kind]}）—— ${h.reason}`,
    ),
    "",
    AGENT_FOOTER,
  ].join("\n");

  return {
    text: body,
    trace: [...trace, "生成回答：按「经验 → 日记 → 知识」的优先级组织"],
    refs: hits.map((h) => ({ kind: h.kind, id: h.id, title: h.title, reason: h.reason })),
  };
}

/** Agent 问答入口：先走预设，再回落本地检索（全程 Mock，不联网） */
export function answerQuestion(question: string, data: LibraryData): AgentAnswer {
  return presetAnswer(question) ?? libraryAnswer(question, data);
}
