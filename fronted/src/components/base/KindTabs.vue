<template>
  <div class="seg">
    <button
      v-for="option in options"
      :key="String(option.value)"
      :class="{ on: option.value === modelValue }"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
      <span v-if="option.count !== undefined" class="faint"> {{ option.count }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
interface Option {
  value: string;
  label: string;
  count?: number;
}

defineProps<{ modelValue: string; options: Option[] }>();
const emit = defineEmits<{ (e: "update:modelValue", v: string): void }>();
</script>
