import type { DiaryDraft, DiaryKind } from "@/types";
import { todayISO, truncate, uid } from "./format";
import { searchLibrary, type LibraryData } from "./search";
import { extractKeywords, type TopicGuess } from "./topic";

const SECTION_TITLES = [
  "今天发生了什么",
  "为什么重要",
  "遇到的问题",
  "怎么解决的",
  "学到了什么",
];

const PLACEHOLDER = "（这一部分我没有问到，稍后可以自己补充）";

function kindSuffix(kind: DiaryKind): string {
  switch (kind) {
    case "技术":
      return "踩坑记录";
    case "复盘":
      return "复盘";
    case "学习":
      return "学习笔记";
    case "项目":
      return "项目记录";
    case "问题":
      return "问题记录";
    case "思考":
      return "思考笔记";
    case "日常":
      return "日常记录";
    default:
      return "记录";
  }
}

/** 采访完成 → 生成日记草稿（Mock AI 的结构化产出） */
export function buildDraft(answers: string[], topic: TopicGuess, data: LibraryData): DiaryDraft {
  const scenario = topic.scenario;
  const cleaned = answers.map((a) => (a ?? "").trim());
  const kind: DiaryKind = scenario?.kind ?? "复盘";

  const sections = SECTION_TITLES.map((title, index) => {
    const answer = cleaned[index] ?? "";
    return `## ${title}\n\n${answer || PLACEHOLDER}`;
  });

  const searchText = cleaned.join(" ") || topic.label;
  const hits = searchLibrary(searchText, data, 6).filter((h) => h.kind === "knowledge");

  const suggestedKnowledge = scenario
    ? scenario.knowledge.map((k) => ({ ...k }))
    : hits.slice(0, 3).map((h) => ({
        title: h.title,
        existingId: h.id,
        reason: "与本次采访内容相关",
      }));

  if (!suggestedKnowledge.length) {
    suggestedKnowledge.push({
      title: `${topic.label}：本次新学到的一条`,
      reason: "知识库里还没有对应条目，建议新建",
    });
  }

  const wikiLine = suggestedKnowledge
    .filter((k) => k.existingId)
    .map((k) => `[[${k.title}]]`)
    .join("、");
  const relatedLine = wikiLine
    ? `\n\n> 本次内容与已有知识关联：${wikiLine}`
    : "\n\n> 本次内容暂未关联已有知识条目。";

  const background = scenario ? `> ${scenario.background}\n\n` : "";
  const content = [
    background + sections.join("\n\n"),
    "---",
    `\n*本篇由 MemoAgent 采访整理于 ${todayISO()}，草稿状态，可继续编辑。*${relatedLine}`,
  ].join("\n\n");

  const extraction = scenario
    ? { ...scenario.extraction }
    : {
        problem: cleaned[2] || PLACEHOLDER,
        cause: cleaned[1] || PLACEHOLDER,
        solution: cleaned[3] || PLACEHOLDER,
        lesson: cleaned[4] || PLACEHOLDER,
      };

  const summary = scenario
    ? scenario.summary
    : truncate(cleaned[0] || `${topic.label}的记录`, 60);

  const title = scenario
    ? `${scenario.label}：${kindSuffix(kind)}`
    : `${topic.label} · ${kindSuffix(kind)}`;

  const tags = scenario ? [...scenario.tags] : [...new Set(topic.tags.length ? topic.tags : extractKeywords(searchText, 3))];

  return {
    id: uid("draft"),
    title,
    date: todayISO(),
    kind,
    tags: tags.slice(0, 5),
    summary,
    content,
    extraction,
    suggestedKnowledge,
    saved: false,
  };
}
