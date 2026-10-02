<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, NEmpty, NInput, NSpace } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { analysisApi } from '../api'

/** The runner's stderr log (upstream logging, tracebacks), with a filter. */
const props = defineProps<{ analysisId: string; live: boolean }>()
const { t } = useI18n()
const lines = ref<string[]>([])
const filter = ref('')
const loading = ref(false)

const visible = computed(() => {
  const needle = filter.value.trim().toLowerCase()
  return needle ? lines.value.filter((line) => line.toLowerCase().includes(needle)) : lines.value
})

const level = (line: string) =>
  /\b(ERROR|CRITICAL|Traceback)\b/.test(line) ? 'error' : /\bWARNING\b/.test(line) ? 'warn' : ''

async function load() {
  loading.value = true
  try {
    lines.value = (await analysisApi.getLogs(props.analysisId)).lines
  } catch {
    lines.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(
  () => props.live,
  (live, wasLive) => wasLive && !live && load(),
)
</script>

<template>
  <NSpace vertical>
    <NSpace>
      <NInput
        v-model:value="filter"
        :placeholder="t('detail.logsFilter')"
        clearable
        style="width: 260px"
      />
      <NButton :loading="loading" @click="load">{{ t('detail.refreshLogs') }}</NButton>
    </NSpace>
    <NEmpty v-if="!lines.length && !loading" :description="t('detail.noLogs')" />
    <pre v-else class="log"><span
        v-for="(line, index) in visible"
        :key="index"
        :class="['log__line', `log__line--${level(line)}`]"
      >{{ line }}
</span></pre>
  </NSpace>
</template>

<style scoped>
.log {
  margin: 0;
  max-height: 560px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.log__line--error {
  color: #e88080;
}

.log__line--warn {
  color: #f2c97d;
}
</style>
