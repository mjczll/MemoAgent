import { defineStore } from "pinia";
import type { ProfileSettings, ToastItem } from "@/types";
import { getProfile } from "@/api/profile";
import { uid } from "@/lib/format";

type ThemeName = "dark";

const SIDEBAR_KEY = "memoagent:sidebar";
const PROFILE_KEY = "memoagent:profile";

export type { ProfileSettings } from "@/types";

const DEFAULT_PROFILE: ProfileSettings = {
  displayName: "阿七",
  signature: "把每一次踩坑都变成可复用的经验",
  goalWeekly: 5,
  reminderTime: "21:30",
  reviewDay: 0,
  reviewTime: "21:30",
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
      // 偏好以后端为准（阶段 8.2），失败时保留 localStorage 缓存值
      void this.loadProfile();
    },

    async loadProfile() {
      try {
        const remote = await getProfile();
        this.profile = {
          displayName: remote.displayName || this.profile.displayName,
          signature: remote.signature ?? this.profile.signature,
          goalWeekly: remote.goalWeekly > 0 ? remote.goalWeekly : this.profile.goalWeekly,
          reminderTime: remote.reminderTime ?? this.profile.reminderTime,
          reviewDay: typeof remote.reviewDay === "number" ? remote.reviewDay : this.profile.reviewDay,
          reviewTime: remote.reviewTime || this.profile.reviewTime,
          autoExtract: remote.autoExtract,
          autoSuggestKnowledge: remote.autoSuggestKnowledge,
          weeklyReview: remote.weeklyReview,
        };
        localStorage.setItem(PROFILE_KEY, JSON.stringify(this.profile));
      } catch {
        // 后端不可用时静默保留本地值
      }
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
