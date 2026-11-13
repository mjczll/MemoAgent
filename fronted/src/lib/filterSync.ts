import { reactive, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

/**
 * 筛选状态与 URL 的单向同步。
 *
 * 数据流（只允许这一个方向触发加载）：
 *   UI 事件 → 更新 filters → writeRoute()（router.replace）
 *   URL 变化 → watch(route.fullPath) → applyRoute() → onChange()
 *
 * 这样 URL 是可持久化的筛选状态，filters 是运行时状态，
 * 两者不会互相无条件监听，天然避免重复请求。
 *
 * 规则（项目规范）：
 * - 筛选变化用 router.replace，不产生历史记录；
 * - 页面跳转才用 router.push；
 * - 任何筛选变化都要把 page 归 1，否则会出现「只有 2 条数据却在请求第 5 页」。
 */
export interface FilterSyncOptions {
  /** 路由名守卫：只在当前页面时响应 query 变化 */
  routeName: string;
  /** 各筛选的默认值；写回 URL 时与默认值相同的项会被省略 */
  defaults: Record<string, string>;
  /** URL 应用到 filters 之后的回调（服务端筛选的页面在这里重新加载） */
  onChange?: () => void;
}

export function useFilterSync(options: FilterSyncOptions) {
  const route = useRoute();
  const router = useRouter();

  const filters = reactive<Record<string, string>>({ ...options.defaults });

  function readRoute(): Record<string, string> {
    const next: Record<string, string> = { ...options.defaults };
    for (const key of Object.keys(options.defaults)) {
      const raw = route.query[key];
      const value = Array.isArray(raw) ? raw[0] : raw;
      if (typeof value === "string" && value !== "") {
        next[key] = value;
      }
    }
    return next;
  }

  /** 把 URL 上的筛选写回 filters，返回是否有变化。 */
  function applyRoute(): boolean {
    const next = readRoute();
    let changed = false;
    for (const key of Object.keys(next)) {
      if (filters[key] !== next[key]) {
        filters[key] = next[key];
        changed = true;
      }
    }
    return changed;
  }

  function buildQuery(): Record<string, string> {
    const query: Record<string, string> = {};
    for (const key of Object.keys(options.defaults)) {
      const value = (filters[key] ?? "").trim();
      if (value && value !== options.defaults[key]) {
        query[key] = value;
      }
    }
    return query;
  }

  /** 把当前筛选写进 URL。筛选变化后调用；page 归 1 由调用方先改 filters。 */
  function writeRoute() {
    void router.replace({ query: buildQuery() });
  }

  watch(
    () => route.fullPath,
    () => {
      if (route.name !== options.routeName) return;
      applyRoute();
      options.onChange?.();
    },
  );

  return { filters, applyRoute, writeRoute };
}
