<script setup lang="ts">
import { NAlert, NButton, NInput, NModal, NSpace, useMessage } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'

const props = defineProps<{ show: boolean; username: string; password: string | null }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useLabels()
const message = useMessage()

async function copy() {
  if (props.password === null) return
  try {
    await navigator.clipboard.writeText(props.password)
    message.success(t('users.oneTimePassword.copied'))
  } catch {
    // The field stays selectable by hand.
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="t('users.oneTimePassword.title')"
    :mask-closable="false"
    style="max-width: 480px"
    @update:show="emit('close')"
  >
    <NSpace vertical :size="12">
      <NAlert type="warning" :show-icon="false">
        {{
          password === null
            ? t('users.oneTimePassword.entered', { username })
            : t('users.oneTimePassword.shownOnce', { username })
        }}
      </NAlert>
      <div>
        <div class="label">{{ t('users.oneTimePassword.user') }}</div>
        <code>{{ username }}</code>
      </div>
      <div v-if="password !== null">
        <div class="label">{{ t('users.oneTimePassword.password') }}</div>
        <NSpace :wrap="false">
          <NInput :value="password" readonly style="font-family: monospace; min-width: 280px" />
          <NButton @click="copy">{{ t('users.oneTimePassword.copy') }}</NButton>
        </NSpace>
      </div>
    </NSpace>
    <template #footer>
      <NButton type="primary" @click="emit('close')">{{
        t('users.oneTimePassword.close')
      }}</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.label {
  opacity: 0.75;
  margin-bottom: 4px;
}
</style>
