export function normalizeKind(name: string): string {
  return name.trim().slice(0, 32);
}
