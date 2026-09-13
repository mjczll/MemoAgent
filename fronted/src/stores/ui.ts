import { defineStore } from "pinia";
import type { ToastItem } from "@/types";
import { uid } from "@/lib/format";

type ThemeName = "dark";

const SIDEBAR_KEY = "memoagent:sidebar";
const PROFILE_KEY = "memoagent:profile";

export interface ProfileSettings {
  displayName: string;
  signature: string;
  goalWeekly: number;
  reminderTime: string;
  autoExtract: boolean;
  autoSuggestKnowledge: boolean;
  weeklyReview: boolean;
}

const DEFAULT_PROFILE: ProfileSettings = {
  displayName: "阿七",
  signature: "把每一次踩坑都变成可复用的经验",
  goalWeekly: 5,
  reminderTime: "21:30",
  autoExtract: true,
  autoSuggestKnowledge: true,
  weeklyReview: true,
};

/** 全局 UI 状态：主题（强制深色雪夜）、侧栏折叠、Toast、个人设置 */
export const useUiStore = defineStore("ui", {
  state: () => ({
    theme: "dark" as ThemeName,
    sidebarCollapsed: false,
    toasts: [] as ToastItem[],
    profile: { ...DEFAULT_PROFILE } as ProfileSettings,
    hydrated: false,
  }),

  getters: {
    isDark(state): boolean {
      return state.theme === "dark";
    },
  },

  actions: {
    hydrate() {
      if (this.hydrated) return;
      // 强制深色，忽略持久化的浅色偏好
      this.theme = "dark";
      this.sidebarCollapsed = localStorage.getItem(SIDEBAR_KEY) === "1";
      try {
        const raw = localStorage.getItem(PROFILE_KEY);
        if (raw) this.profile = { ...DEFAULT_PROFILE, ...(JSON.parse(raw) as ProfileSettings) };
      } catch {
        this.profile = { ...DEFAULT_PROFILE };
      }
      this.applyTheme();
      this.hydrated = true;
    },

    applyTheme() {
      const root = document.documentElement;
      root.dataset.theme = this.theme;
      root.classList.add("theme-dark");
      root.classList.remove("theme-light");
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
      localStorage.setItem(SIDEBAR_KEY, this.sidebarCollapsed ? "1" : "0");
    },

    saveProfile(patch: Partial<ProfileSettings>) {
      this.profile = { ...this.profile, ...patch };
      localStorage.setItem(PROFILE_KEY, JSON.stringify(this.profile));
    },

    toast(text: string, kind: ToastItem["kind"] = "ok") {
      const item: ToastItem = { id: uid("t"), text, kind };
      this.toasts.push(item);
      window.setTimeout(() => this.dismiss(item.id), 2600);
    },

    dismiss(id: string) {
      this.toasts = this.toasts.filter((t) => t.id !== id);
    },
  },
});
