<script setup lang="ts">
// Nothing but the title reaches the markup, not even as a stray attribute.
defineOptions({ inheritAttrs: false })
defineProps<{ title: string }>()

const LINES = [96, 88, 92, 70, 84, 55]
</script>

<template>
  <section class="locked">
    <h3 class="locked__title">{{ title }}</h3>
    <div class="locked__lines" aria-hidden="true">
      <span v-for="(width, index) in LINES" :key="index" :style="{ width: `${width}%` }" />
    </div>
  </section>
</template>

<style scoped>
.locked {
  border: 1px solid rgba(128, 128, 128, 0.25);
  border-radius: 6px;
  padding: 12px 16px;
}

.locked__title {
  margin: 0 0 12px;
}

.locked__lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
  filter: blur(3px);
  user-select: none;
}

.locked__lines span {
  display: block;
  height: 10px;
  border-radius: 5px;
  background: rgba(128, 128, 128, 0.45);
}
</style>
