<template>
  <teleport to="body">
    <div v-if="modelValue" class="modal-scrim" @click.self="close">
      <div class="modal" :class="{ 'modal-confirm': variant === 'confirm' }" :style="{ maxWidth: width }">
        <div v-if="variant !== 'confirm'" class="modal-head">
          <b>{{ title }}</b>
          <button class="icon-btn" title="关闭" @click="close">✕</button>
        </div>
        <div v-else class="modal-confirm-title">{{ title }}</div>
        <div class="modal-body">
          <slot />
        </div>
        <div v-if="$slots.foot" class="modal-foot" :class="{ 'modal-foot-end': variant === 'confirm' }">
          <slot name="foot" />
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    width?: string;
    /** confirm：确认卡片——无关闭按钮、标题融入正文、按钮右对齐、无分割线 */
    variant?: "default" | "confirm";
  }>(),
  { width: "620px", variant: "default" },
);

const emit = defineEmits<{ (e: "update:modelValue", v: boolean): void }>();

function close() {
  emit("update:modelValue", false);
}
</script>
