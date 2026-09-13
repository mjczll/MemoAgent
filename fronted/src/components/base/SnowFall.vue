<template>
  <div class="field-bg" :style="{ opacity: ui.isDark ? 1 : 0.45 }">
    <span
      v-for="flake in flakes"
      :key="flake.id"
      class="snowflake"
      :class="{ spark: flake.spark }"
      :style="{
        left: `${flake.left}%`,
        width: `${flake.size}px`,
        height: `${flake.size}px`,
        animationDuration: `${flake.duration}s`,
        animationDelay: `${flake.phase}s`,
        '--flake-vis': flake.opacity,
        '--drift': flake.drift,
      } as any"
    />
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();

interface Flake {
  id: number;
  left: number;
  size: number;
  duration: number;
  /** 负值：用负的 animationDelay 让雪花"已经在屏幕里"——避免同时从顶部冒出来 */
  phase: number;
  opacity: number;
  drift: number;
  spark: boolean;
}

const COUNT = 52;

/* ---------------- 高质量确定性随机（Mulberry32） ---------------- */
function mulberry32(seed: number) {
  let s = seed >>> 0;
  return function () {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* ---------------- Poisson-disc 抽样（最小间距 6.5%） ---------------- */
const MIN_GAP = 6.5;
const MAX_RETRY = 30;

function buildFlakes(): Flake[] {
  // 每次构建生成一份独立 RNG
  const rng = mulberry32(0xa17e9b);
  const samples: number[] = [];
  const list: Flake[] = [];

  for (let i = 0; i < COUNT; i += 1) {
    // 1) 横向位置：随机 + 最小间距拒绝采样（避免两两挨在一起）
    let left = rng() * 100;
    let guard = 0;
    while (samples.some((s) => Math.abs(s - left) < MIN_GAP) && guard < MAX_RETRY) {
      left = rng() * 100;
      guard += 1;
    }
    samples.push(left);

    // 2) 大小：3-5px 为主，约 12% 是 6-8px 亮片
    const big = rng() < 0.12;
    const size = big ? 5 + Math.round(rng() * 3) : 2 + Math.round(rng() * 3);

    // 3) 持续时间：18 - 40s，大颗粒稍慢
    const duration = big ? 22 + rng() * 18 : 18 + rng() * 22;

    // 4) 入场相位：用 duration 自己的负数倍（独立随机），让初始位置完全不同
    const phase = -(rng() * duration);

    // 5) 透明度：0.55 - 0.95
    const opacity = 0.55 + rng() * 0.4;

    // 6) 漂移：-9 ~ 12 vw（更宽的随机范围）
    const drift = Math.round((rng() * 21 - 9) * 10) / 10;

    list.push({
      id: i,
      left: Math.round(left * 100) / 100,
      size,
      duration: Math.round(duration * 10) / 10,
      phase: Math.round(phase * 10) / 10,
      opacity: Math.round(opacity * 100) / 100,
      drift,
      spark: big,
    });
  }

  return list;
}

const flakes = buildFlakes();
</script>