<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NForm, NModal } from 'naive-ui'
import { weakPasswordMessages } from '@/features/auth'
import { ApiError } from '@/shared/api/error'
import type { User } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { usersApi } from '../api'
import PasswordChoice from './PasswordChoice.vue'

const props = defineProps<{ show: boolean; user: User | null; currentUserId: string }>()
const emit = defineEmits<{ close: []; reset: [user: User, oneTimePassword: string | null] }>()

const { t, error: errorText } = useLabels()

const password = ref<string | null>(null)
const submitting = ref(false)
const passwordMessages = ref<string[]>([])
const error = ref<string | null>(null)

watch(
  () => props.show,
  (show) => {
    if (!show) return
    password.value = null
    passwordMessages.value = []
    error.value = null
  },
)

const own = computed(() => props.user?.id === props.currentUserId)

async function submit() {
  if (!props.user || submitting.value) return
  submitting.value = true
  passwordMessages.value = []
  error.value = null
  try {
    const result = await usersApi.resetPassword(props.user.id, password.value ?? undefined)
    emit('reset', props.user, result.oneTimePassword)
  } catch (e) {
    if (e instanceof ApiError && e.errorCode === 'weak_password') {
      passwordMessages.value = weakPasswordMessages(e.params).map((m) => t(m.key, m.params))
    } else {
      error.value = errorText(e)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal :show="show" @update:show="emit('close')">
    <NCard
      :title="t('users.resetPassword.title', { username: user?.username ?? '' })"
      closable
      style="max-width: 480px"
      @close="emit('close')"
    >
      <NForm @submit.prevent="submit">
        <NAlert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</NAlert>
        <p class="note">
          {{ own ? t('users.resetPassword.ownAccount') : t('users.resetPassword.sessionsEnd') }}
        </p>
        <PasswordChoice v-model="password" :messages="passwordMessages" />
        <NButton type="primary" attr-type="submit" :loading="submitting">
          {{ t('users.resetPassword.submit') }}
        </NButton>
      </NForm>
    </NCard>
  </NModal>
</template>

<style scoped>
.note {
  margin: 0 0 16px;
  opacity: 0.75;
}
</style>
