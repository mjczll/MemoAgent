export function normalizeDomain(name: string): string {
  return name.trim().slice(0, 64);
}

export function collectDomains(items: Array<{ domain?: string }>): string[] {
  const counts = new Map<string, number>();
  for (const item of items) {
    const name = item.domain?.trim();
    if (!name) continue;
    counts.set(name, (counts.get(name) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "zh"))
    .map(([name]) => name);
}
