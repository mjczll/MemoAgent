/** 日期与文案格式化工具 */

export function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** 今天日期 YYYY-MM-DD */
export function todayISO(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 精确时间戳 YYYY-MM-DD HH:mm */
export function nowStamp(): string {
  const d = new Date();
  return `${todayISO()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** 2026-08-07 → 2026年08月07日 */
export function cnDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  if (!y || !m || !d) return iso;
  return `${y}年${m}月${d}日`;
}

/** 相对时间：3 天前 / 刚刚 */
export function relative(iso: string): string {
  const target = new Date(iso.replace(" ", "T")).getTime();
  if (Number.isNaN(target)) return iso;
  const diff = Date.now() - target;
  const day = 86400000;
  if (diff < 60000) return "刚刚";
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  if (diff < day) return `${Math.floor(diff / 3600000)} 小时前`;
  if (diff < day * 30) return `${Math.floor(diff / day)} 天前`;
  return iso.slice(0, 10);
}

/** 生成一个稳定的唯一 id（原型内够用，不追求全局唯一） */
export function uid(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

/** 截断文本 */
export function truncate(text: string, max: number): string {
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}

export function plural(n: number, unit: string): string {
  return `${n} ${unit}`;
}
