<template>
  <button
    type="button"
    class="switch"
    role="switch"
    :aria-checked="modelValue ? 'true' : 'false'"
    :disabled="disabled"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span class="knob" />
  </button>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ modelValue: boolean; disabled?: boolean }>(), { disabled: false });

defineEmits<{ "update:modelValue": [boolean] }>();
</script>

<style scoped>
.switch {
  position: relative;
  flex: none;
  width: 38px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface-2);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.switch[aria-checked="true"] {
  border-color: var(--ink);
  background: var(--ink);
}

.knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--mute);
  transition:
    transform 0.2s cubic-bezier(0.4, 0, 0.2, 1),
    background 0.2s ease;
}

.switch[aria-checked="true"] .knob {
  transform: translateX(16px);
  background: var(--accent-ink);
}

.switch:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.switch:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--accent-line);
}
</style>
