import { createRouter, createWebHashHistory, type RouteRecordRaw } from "vue-router";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "home",
    component: () => import("@/views/HomeView.vue"),
    meta: { title: "首页", crumb: "首页" },
  },
  {
    path: "/interview",
    name: "interview",
    component: () => import("@/views/InterviewView.vue"),
    meta: { title: "开始采访", crumb: "采访现场" },
  },
  {
    path: "/diaries",
    name: "diaries",
    component: () => import("@/views/DiariesView.vue"),
    meta: { title: "日记", crumb: "日记" },
  },
  {
    path: "/diaries/new",
    name: "diary-new",
    component: () => import("@/views/DiaryEditorView.vue"),
    meta: { title: "写日记", crumb: "日记 / 新建" },
  },
  {
    path: "/diaries/draft/:id",
    name: "diary-draft",
    component: () => import("@/views/DiaryDraftView.vue"),
    meta: { title: "采访草稿", crumb: "日记 / 采访草稿" },
  },
  {
    path: "/diaries/:id/edit",
    name: "diary-edit",
    component: () => import("@/views/DiaryEditorView.vue"),
    meta: { title: "编辑日记", crumb: "日记 / 编辑" },
  },
  {
    path: "/diaries/:id",
    name: "diary-detail",
    component: () => import("@/views/DiaryDetailView.vue"),
    meta: { title: "日记详情", crumb: "日记 / 详情" },
  },
  {
    path: "/experiences",
    name: "experiences",
    component: () => import("@/views/ExperiencesView.vue"),
    meta: { title: "经验", crumb: "经验" },
  },
  {
    path: "/experiences/:id",
    name: "experience-detail",
    component: () => import("@/views/ExperienceDetailView.vue"),
    meta: { title: "经验详情", crumb: "经验 / 详情" },
  },
  {
    path: "/knowledge",
    name: "knowledge",
    component: () => import("@/views/KnowledgeView.vue"),
    meta: { title: "知识库", crumb: "知识库" },
  },
  {
    path: "/knowledge/:id",
    name: "knowledge-detail",
    component: () => import("@/views/KnowledgeDetailView.vue"),
    meta: { title: "知识详情", crumb: "知识库 / 详情" },
  },
  {
    path: "/graph",
    name: "graph",
    component: () => import("@/views/GraphView.vue"),
    meta: { title: "知识织网", crumb: "知识织网" },
  },
  {
    path: "/graph/local/:id",
    name: "graph-local",
    component: () => import("@/views/GraphView.vue"),
    meta: { title: "局部关系图", crumb: "知识织网 / 局部图" },
  },
  {
    path: "/agent",
    name: "agent",
    component: () => import("@/views/AgentView.vue"),
    meta: { title: "问 Agent", crumb: "Agent 问答" },
  },
  {
    path: "/search",
    name: "search",
    component: () => import("@/views/SearchView.vue"),
    meta: { title: "搜索", crumb: "搜索" },
  },
  {
    path: "/settings",
    name: "settings",
    component: () => import("@/views/SettingsView.vue"),
    meta: { title: "设置", crumb: "设置" },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "not-found",
    component: () => import("@/views/NotFoundView.vue"),
    meta: { title: "未找到", crumb: "404" },
  },
];

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) ?? "MemoAgent";
  document.title = `${title} · MemoAgent`;
});
