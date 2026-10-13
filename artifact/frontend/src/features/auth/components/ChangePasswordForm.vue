<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { ApiError } from '@/shared/api/error'
import { useLabels } from '@/shared/composables/useLabels'
import { weakPasswordMessages, type PolicyMessage } from '../model/passwordPolicy'
import { useSessionStore } from '../store'

const emit = defineEmits<{ changed: [] }>()

const { t, error: errorText } = useLabels()
const session = useSessionStore()

const currentPassword = ref('')
const newPassword = ref('')
const repeatPassword = ref('')
const submitting = ref(false)
const repeatTouched = ref(false)
// The three answers that belong to a field show next to it, any other error above the form.
const fieldError = ref<'wrong_current_password' | 'password_unchanged' | 'weak_password' | null>(
  null,
)
const policyMessages = ref<PolicyMessage[]>([])
const error = ref<string | null>(null)

const mismatch = computed(() => repeatTouched.value && repeatPassword.value !== newPassword.value)

async function submit() {
  if (submitting.value) return
  repeatTouched.value = true
  if (repeatPassword.value !== newPassword.value) return
  submitting.value = true
  fieldError.value = null
  error.value = null
  try {
    await session.changePassword(currentPassword.value, newPassword.value)
    emit('changed')
  } catch (e) {
    if (
      e instanceof ApiError &&
      (e.errorCode === 'wrong_current_password' || e.errorCode === 'password_unchanged')
    ) {
      fieldError.value = e.errorCode
    } else if (e instanceof ApiError && e.errorCode === 'weak_password') {
      policyMessages.value = weakPasswordMessages(e.params)
      fieldError.value = 'weak_password'
    } else {
      error.value = errorText(e)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NForm @submit.prevent="submit">
    <NAlert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</NAlert>
    <NFormItem
      :label="t('password.current')"
      :validation-status="fieldError === 'wrong_current_password' ? 'error' : undefined"
      :feedback="
        fieldError === 'wrong_current_password' ? t('errors.wrong_current_password') : undefined
      "
    >
      <NInput
        v-model:value="currentPassword"
        type="password"
        :input-props="{ autocomplete: 'current-password', name: 'current-password' }"
        :placeholder="t('password.current')"
      />
    </NFormItem>
    <NFormItem
      :label="t('password.new')"
      :validation-status="
        fieldError === 'password_unchanged' || fieldError === 'weak_password' ? 'error' : undefined
      "
      :feedback="fieldError === 'password_unchanged' ? t('errors.password_unchanged') : undefined"
    >
      <NInput
        v-model:value="newPassword"
        type="password"
        :input-props="{ autocomplete: 'new-password', name: 'new-password' }"
        :placeholder="t('password.new')"
        @update:value="fieldError === 'weak_password' && (fieldError = null)"
      />
      <template v-if="fieldError === 'weak_password'" #feedback>
        <div v-for="message in policyMessages" :key="message.key">
          {{ t(message.key, message.params) }}
        </div>
      </template>
    </NFormItem>
    <NFormItem
      :label="t('password.repeat')"
      :validation-status="mismatch ? 'error' : undefined"
      :feedback="mismatch ? t('password.mismatch') : undefined"
    >
      <NInput
        v-model:value="repeatPassword"
        type="password"
        :input-props="{ autocomplete: 'new-password', name: 'repeat-password' }"
        :placeholder="t('password.repeat')"
        @blur="repeatTouched = repeatPassword !== ''"
        @keydown.enter.prevent="submit"
      />
    </NFormItem>
    <NButton type="primary" attr-type="submit" block :loading="submitting">
      {{ t('password.submit') }}
    </NButton>
  </NForm>
</template>
