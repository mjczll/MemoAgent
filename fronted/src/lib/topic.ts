import type { DomainName } from "@/types";
import { DOMAINS } from "@/mock/domains";
import { TECH_TERMS } from "./terms";
import { SCENARIOS, type ScenarioPreset } from "@/mock/interview";

export interface TopicGuess {
  /** 命中的场景 key，未命中为 null */
  scenario: ScenarioPreset | null;
  /** 显示用的主题名，如「Spring AI」 */
  label: string;
  domain: DomainName | string;
  tags: string[];
}

const STOP_WORDS = new Set([
  "今天",
  "昨天",
  "明天",
  "然后",
  "就是",
  "这个",
  "那个",
  "这些",
  "那些",
  "问题",
  "事情",
  "感觉",
  "觉得",
  "一下",
  "一个",
  "有点",
  "还是",
  "因为",
  "所以",
  "但是",
  "而且",
  "如果",
  "我们",
  "你们",
  "他们",
  "咱们",
  "自己",
  "之前",
  "以后",
  "以前",
  "后来",
  "现在",
  "当时",
  "什么",
  "怎么",
  "怎样",
  "如何",
  "为什么",
  "哪个",
  "哪些",
  "应该",
  "可以",
  "能否",
  "是否",
  "有没有",
  "遇到",
  "碰到",
  "出现",
  "发生",
  "需要",
  "想要",
  "希望",
  "帮我",
  "告诉",
  "说说",
  "讲讲",
  "总结",
  "看看",
  "请问",
  "比较",
  "非常",
  "特别",
  "知道",
  "记得",
]);

/** 单字虚词：切分时直接抹掉，保留实义片段 */
const PARTICLES = [
  "的",
  "了",
  "着",
  "过",
  "吗",
  "呢",
  "吧",
  "啊",
  "呀",
  "哦",
  "嗯",
  "就",
  "都",
  "也",
  "还",
  "又",
  "很",
  "太",
  "再",
  "把",
  "被",
  "让",
  "给",
  "对",
  "和",
  "与",
  "在",
  "是",
  "有",
  "我",
  "你",
  "他",
  "她",
  "它",
  "们",
  "这",
  "那",
  "个",
  "上",
  "下",
  "里",
  "中",
  "到",
  "从",
  "会",
  "要",
  "能",
  "说",
  "做",
  "先",
  "后",
];

/** 归一化：去掉虚词与标点，把内容片段切成可匹配的词块 */
function normalize(text: string): string {
  let out = text.toLowerCase();
  PARTICLES.forEach((p) => {
    out = out.split(p).join(" ");
  });
  STOP_WORDS.forEach((w) => {
    out = out.split(w).join(" ");
  });
  return out.replace(/[^0-9a-z\u4e00-\u9fa5+#.\-]/g, " ");
}

/**
 * 抽取内容片段：中文按 2~4 字滑窗、英文数字整段保留。
 * Mock 检索的关键词来源，长词优先（更具体）。
 */
export function contentTerms(text: string, max = 8): string[] {
  const terms: string[] = [];
  const push = (t: string) => {
    if (t.length >= 2 && !terms.includes(t)) terms.push(t);
  };

  normalize(text)
    .split(/\s+/)
    .forEach((seg) => {
      if (!seg) return;
      if (/^[0-9a-z+#.\-]+$/.test(seg)) {
        push(seg);
        return;
      }
      for (let len = Math.min(4, seg.length); len >= 2; len -= 1) {
        for (let i = 0; i + len <= seg.length; i += 1) push(seg.slice(i, i + len));
      }
    });

  return terms.sort((a, b) => b.length - a.length).slice(0, max);
}

/** 从一段自由文本中粗略提取关键词（Mock 主题识别，不做真实 NLP） */
export function extractKeywords(text: string, max = 4): string[] {
  const lower = text.toLowerCase();
  const ordered: string[] = [];

  TECH_TERMS.forEach((term) => {
    if (lower.includes(term)) ordered.push(term);
  });

  contentTerms(text, max + 3).forEach((term) => {
    if (ordered.length >= max) return;
    if (!ordered.some((o) => o.includes(term) || term.includes(o))) ordered.push(term);
  });

  return ordered.slice(0, max);
}

/** 主题识别：先匹配演示场景，再退化为领域关键词匹配 */
export function inferTopic(text: string): TopicGuess {
  const lower = text.toLowerCase();

  const scenario =
    SCENARIOS.find((s) => s.keywords.some((k) => lower.includes(k.toLowerCase()))) ?? null;
  if (scenario) {
    return { scenario, label: scenario.label, domain: scenario.domain, tags: scenario.tags };
  }

  const domain =
    DOMAINS.find((d) => d.keywords.some((k) => lower.includes(k.toLowerCase())))?.name ??
    "项目开发";

  const keywords = extractKeywords(text, 1);
  return {
    scenario: null,
    label: keywords[0] ?? "今天的一件事",
    domain,
    tags: extractKeywords(text, 3),
  };
}

/** 生成回显片段：把用户的长回答压缩成一句可引用的短语 */
export function echo(text: string, max = 24): string {
  const flat = text.replace(/\s+/g, " ").trim();
  if (flat.length <= max) return flat;
  return `${flat.slice(0, max)}…`;
}

/** 把追问模板里的 {echo} 占位替换为真实回答片段 */
export function fillTemplate(template: string, vars: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => vars[key] ?? "");
}
