/**
 * 轻量 Markdown 渲染器（原型用）。
 * 只支持 Mock 数据里实际用到的语法：标题、段落、列表、引用、代码块、行内代码、
 * 加粗、分割线、表格，以及 MemoAgent 特有的 [[WikiLink]] 双向链接。
 */

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

/** 行内语法：加粗、行内代码、WikiLink */
function renderInline(raw: string): string {
  let out = escapeHtml(raw);

  // [[知识标题]] → 可点击的双链
  out = out.replace(/\[\[([^\]]+)\]\]/g, (_m, title: string) => {
    return `<a class="wikilink" data-wikilink="${title.trim()}">${title.trim()}</a>`;
  });

  // 行内代码
  out = out.replace(/`([^`]+)`/g, (_m, code: string) => `<code>${code}</code>`);

  // 加粗
  out = out.replace(/\*\*([^*]+)\*\*/g, (_m, strong: string) => `<strong>${strong}</strong>`);

  return out;
}

function isTableSeparator(line: string): boolean {
  return /^\s*\|?[\s:-]+\|[\s|:-]*$/.test(line) && line.includes("-");
}

/** 把 Markdown 文本渲染为 HTML 字符串 */
export function renderMarkdown(markdown: string): string {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const html: string[] = [];

  let listBuffer: string[] = [];
  let listOrdered = false;
  let paragraphBuffer: string[] = [];
  let inCode = false;
  let codeBuffer: string[] = [];

  const flushParagraph = () => {
    if (paragraphBuffer.length) {
      html.push(`<p>${renderInline(paragraphBuffer.join(" "))}</p>`);
      paragraphBuffer = [];
    }
  };

  const flushList = () => {
    if (listBuffer.length) {
      const tag = listOrdered ? "ol" : "ul";
      html.push(`<${tag}>${listBuffer.map((item) => `<li>${renderInline(item)}</li>`).join("")}</${tag}>`);
      listBuffer = [];
    }
  };

  const flushAll = () => {
    flushParagraph();
    flushList();
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i] ?? "";
    const trimmed = line.trim();

    // 代码块
    if (trimmed.startsWith("```")) {
      if (inCode) {
        html.push(`<pre><code>${escapeHtml(codeBuffer.join("\n"))}</code></pre>`);
        codeBuffer = [];
        inCode = false;
      } else {
        flushAll();
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      codeBuffer.push(line);
      continue;
    }

    // 空行
    if (!trimmed) {
      flushAll();
      continue;
    }

    // 分割线
    if (/^-{3,}$/.test(trimmed)) {
      flushAll();
      html.push("<hr />");
      continue;
    }

    // 标题
    const heading = /^(#{1,4})\s+(.*)$/.exec(trimmed);
    if (heading) {
      flushAll();
      const level = Math.min((heading[1] ?? "#").length + 1, 4);
      html.push(`<h${level}>${renderInline(heading[2] ?? "")}</h${level}>`);
      continue;
    }

    // 表格
    if (trimmed.startsWith("|") && isTableSeparator(lines[i + 1] ?? "")) {
      flushAll();
      const parseRow = (row: string): string[] =>
        row
          .trim()
          .replace(/^\||\|$/g, "")
          .split("|")
          .map((cell) => cell.trim());
      const head = parseRow(trimmed);
      const rows: string[][] = [];
      let cursor = i + 2;
      while (cursor < lines.length && (lines[cursor] ?? "").trim().startsWith("|")) {
        rows.push(parseRow(lines[cursor] ?? ""));
        cursor += 1;
      }
      i = cursor - 1;
      html.push(
        `<table class="md-table"><thead><tr>${head
          .map((cell) => `<th>${renderInline(cell)}</th>`)
          .join("")}</tr></thead><tbody>${rows
          .map(
            (row) =>
              `<tr>${row.map((cell) => `<td>${renderInline(cell)}</td>`).join("")}</tr>`,
          )
          .join("")}</tbody></table>`,
      );
      continue;
    }

    // 引用
    if (trimmed.startsWith(">")) {
      flushAll();
      html.push(`<blockquote>${renderInline(trimmed.replace(/^>\s?/, ""))}</blockquote>`);
      continue;
    }

    // 有序列表
    const ordered = /^\d+\.\s+(.*)$/.exec(trimmed);
    if (ordered) {
      if (listBuffer.length && !listOrdered) flushList();
      listOrdered = true;
      listBuffer.push(ordered[1] ?? "");
      continue;
    }

    // 无序列表
    const bullet = /^[-*]\s+(.*)$/.exec(trimmed);
    if (bullet) {
      if (listBuffer.length && listOrdered) flushList();
      listOrdered = false;
      listBuffer.push(bullet[1] ?? "");
      continue;
    }

    // 普通段落
    flushList();
    paragraphBuffer.push(trimmed);
  }

  flushAll();
  if (inCode && codeBuffer.length) {
    html.push(`<pre><code>${escapeHtml(codeBuffer.join("\n"))}</code></pre>`);
  }
  return html.join("\n");
}

/** 从 Markdown 中提取 [[WikiLink]] 标题列表 */
export function extractWikiLinks(markdown: string): string[] {
  const found: string[] = [];
  const re = /\[\[([^\]]+)\]\]/g;
  let match = re.exec(markdown);
  while (match) {
    const title = (match[1] ?? "").trim();
    if (title && !found.includes(title)) found.push(title);
    match = re.exec(markdown);
  }
  return found;
}

/** 纯文本摘要（用于列表副标题） */
export function markdownToPlain(markdown: string, max = 120): string {
  const plain = markdown
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/\[\[([^\]]+)\]\]/g, "$1")
    .replace(/[#>*`|]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return plain.length > max ? `${plain.slice(0, max)}…` : plain;
}
