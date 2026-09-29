<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { localizeReport } from '@/domain/reportLanguage'

/** Renders LLM-written markdown. The output is untrusted input, so the HTML is sanitized. */
const props = defineProps<{ source: string; language?: string | null }>()

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: false })
const html = computed(() =>
  DOMPurify.sanitize(markdown.render(localizeReport(props.source, props.language))),
)
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -- sanitized with DOMPurify above -->
  <article class="markdown" v-html="html" />
</template>

<style scoped>
.markdown {
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.markdown :deep(table) {
  border-collapse: collapse;
  display: block;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.35));
  padding: 4px 8px;
}

.markdown :deep(pre) {
  overflow-x: auto;
}
</style>
