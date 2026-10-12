<script setup lang="ts">
import { ref } from 'vue'
import { NAlert, NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { useLabels } from '@/shared/composables/useLabels'
import { useSessionStore } from '../store'

const emit = defineEmits<{ 'logged-in': [] }>()

const { t, error: errorText } = useLabels()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit() {
  if (submitting.value) return
  submitting.value = true
  error.value = null
  try {
    await session.login(username.value, password.value)
    emit('logged-in')
  } catch (e) {
    error.value = errorText(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NForm @submit.prevent="submit">
    <NAlert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</NAlert>
    <NFormItem :label="t('login.username')">
      <NInput
        v-model:value="username"
        :input-props="{ autocomplete: 'username' }"
        :placeholder="t('login.username')"
      />
    </NFormItem>
    <NFormItem :label="t('login.password')">
      <NInput
        v-model:value="password"
        type="password"
        :input-props="{ autocomplete: 'current-password' }"
        :placeholder="t('login.password')"
        @keydown.enter.prevent="submit"
      />
    </NFormItem>
    <NButton type="primary" attr-type="submit" block :loading="submitting">
      {{ t('login.submit') }}
    </NButton>
  </NForm>
</template>
