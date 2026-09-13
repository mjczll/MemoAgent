import type { Diary, Experience, Knowledge } from "@/types";
import { DOMAINS } from "@/mock/domains";
import { TECH_TERMS } from "./terms";
import { contentTerms } from "./topic";

export interface LibraryData {
  diaries: Diary[];
  experiences: Experience[];
  knowledge: Knowledge[];
}

export type HitKind = "diary" | "experience" | "knowledge";

export interface LibraryHit {
  kind: HitKind;
  id: string;
  title: string;
  reason: string;
  score: number;
}

export { TECH_TERMS };

/** 强关键词：技术词表命中 / 含英文数字 / 中文 3 字以上。用于收紧 Agent 检索精度 */
function isStrongTerm(term: string): boolean {
  return TECH_TERMS.includes(term) || /[0-9a-z]/.test(term) || term.length >= 3;
}

function queryTokens(query: string): string[] {
  const lower = query.toLowerCase();
  const tokens = new Set<string>();

  TECH_TERMS.forEach((term) => {
    if (lower.includes(term)) tokens.add(term);
  });

  // 英文/数字 token（含 504、GC 这类短标识）
  const latin = lower.match(/[a-z0-9][a-z0-9+.#-]{1,}/g) ?? [];
  latin.forEach((t) => tokens.add(t));

  // 中文内容片段（2~4 字滑窗），兜住技术词表里没有的说法
  contentTerms(query, 8).forEach((t) => tokens.add(t));

  return [...tokens];
}

interface Scored {
  item: { id: string; title: string };
  kind: HitKind;
  score: number;
  matched: string[];
}

function scoreOf(
  terms: string[],
  fields: { title: string; tags: string[]; summary?: string; body?: string },
): { score: number; matched: string[] } {
  let score = 0;
  const matched: string[] = [];
  const title = fields.title.toLowerCase();
  const tags = fields.tags.join(" ").toLowerCase();
  const summary = (fields.summary ?? "").toLowerCase();
  const body = (fields.body ?? "").toLowerCase();

  terms.forEach((term) => {
    let hit = false;
    if (title.includes(term)) {
      score += 8;
      hit = true;
    }
    if (tags.includes(term)) {
      score += 5;
      hit = true;
    }
    if (summary.includes(term)) {
      score += 3;
      hit = true;
    }
    if (body.includes(term)) {
      score += 1;
      hit = true;
    }
    if (hit) matched.push(term);
  });

  return { score, matched };
}

function collect(terms: string[], data: LibraryData): Scored[] {
  const out: Scored[] = [];

  data.experiences.forEach((e) => {
    const { score, matched } = scoreOf(terms, {
      title: e.title,
      tags: [...e.tags, e.domain],
      summary: e.problem + e.solution + e.lesson,
    });
    if (score > 0) out.push({ item: e, kind: "experience", score: score + 2, matched });
  });

  data.diaries.forEach((d) => {
    const { score, matched } = scoreOf(terms, {
      title: d.title,
      tags: [...d.tags, d.kind],
      summary: d.summary,
      body: d.content,
    });
    if (score > 0) out.push({ item: d, kind: "diary", score, matched });
  });

  data.knowledge.forEach((k) => {
    const { score, matched } = scoreOf(terms, {
      title: k.title,
      tags: [...k.tags, k.domain, k.category],
      summary: k.summary,
      body: k.content,
    });
    if (score > 0) out.push({ item: k, kind: "knowledge", score, matched });
  });

  return out;
}

/** 在本地知识库中做关键词检索（Mock 版"语义检索"） */
export function searchLibrary(
  query: string,
  data: LibraryData,
  limit = 6,
  minScore = 1,
  strongOnly = false,
): LibraryHit[] {
  const raw = queryTokens(query);
  const terms = strongOnly ? raw.filter(isStrongTerm) : raw;
  if (!terms.length) return [];

  return collect(terms, data)
    .filter((hit) => hit.score >= minScore)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((hit) => ({
      kind: hit.kind,
      id: hit.item.id,
      title: hit.item.title,
      reason: `命中关键词：${[...new Set(hit.matched)].slice(0, 3).join("、")}`,
      score: hit.score,
    }));
}

/** 按领域过滤知识条目 */
export function knowledgeByDomain(data: LibraryData, domain: string): Knowledge[] {
  return data.knowledge.filter((k) => k.domain === domain);
}

/** 领域统计（侧栏与首页用） */
export function domainStats(data: LibraryData): Array<{ name: string; count: number }> {
  return DOMAINS.map((d) => ({
    name: d.name as string,
    count:
      data.diaries.filter((x) => x.tags.includes(d.name) || x.kind === "复盘").length * 0 +
      data.knowledge.filter((k) => k.domain === d.name).length +
      data.experiences.filter((e) => e.domain === d.name).length,
  }));
}
