<script setup lang="ts">
import { ref, watch } from 'vue'
import { NAlert, NButton, NCard, NForm, NFormItem, NInput, NModal } from 'naive-ui'
import { weakPasswordMessages } from '@/features/auth'
import { ApiError } from '@/shared/api/error'
import type { Role, User } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { usersApi } from '../api'
import PasswordChoice from './PasswordChoice.vue'
import RoleSelect from './RoleSelect.vue'

const props = defineProps<{ show: boolean; roles: Role[] }>()
const emit = defineEmits<{
  close: []
  created: [user: User, oneTimePassword: string | null]
}>()

const { t, error: errorText } = useLabels()

const username = ref('')
const roleIds = ref<string[]>([])
const password = ref<string | null>(null)
const submitting = ref(false)
const missing = ref(false)
const usernameError = ref<string | null>(null)
const rolesError = ref<string | null>(null)
const passwordMessages = ref<string[]>([])
const error = ref<string | null>(null)

watch(
  () => props.show,
  (show) => {
    if (!show) return
    username.value = ''
    roleIds.value = []
    password.value = null
    missing.value = false
    usernameError.value = null
    rolesError.value = null
    passwordMessages.value = []
    error.value = null
  },
)

async function submit() {
  if (submitting.value) return
  missing.value = true
  if (!username.value.trim() || roleIds.value.length === 0) return
  submitting.value = true
  usernameError.value = null
  rolesError.value = null
  passwordMessages.value = []
  error.value = null
  try {
    const created = await usersApi.create({
      username: username.value.trim(),
      roleIds: roleIds.value,
      password: password.value ?? undefined,
    })
    emit('created', created.user, created.oneTimePassword)
  } catch (e) {
    const code = e instanceof ApiError ? e.errorCode : null
    if (code === 'invalid_username' || code === 'username_taken') {
      usernameError.value = errorText(e)
    } else if (code === 'unknown_role') {
      rolesError.value = errorText(e)
    } else if (e instanceof ApiError && code === 'weak_password') {
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
      :title="t('users.create.title')"
      closable
      style="max-width: 480px"
      @close="emit('close')"
    >
      <NForm @submit.prevent="submit">
        <NAlert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</NAlert>
        <NFormItem
          :label="t('users.create.username')"
          :validation-status="usernameError || (missing && !username.trim()) ? 'error' : undefined"
          :feedback="
            usernameError ??
            (missing && !username.trim() ? t('users.create.usernameRequired') : undefined)
          "
        >
          <NInput
            v-model:value="username"
            :input-props="{ autocomplete: 'off', name: 'new-username' }"
            @update:value="usernameError = null"
          />
        </NFormItem>
        <NFormItem
          :label="t('users.create.roles')"
          :validation-status="rolesError || (missing && !roleIds.length) ? 'error' : undefined"
          :feedback="
            rolesError ?? (missing && !roleIds.length ? t('users.create.rolesRequired') : undefined)
          "
        >
          <RoleSelect v-model="roleIds" :roles="roles" />
        </NFormItem>
        <PasswordChoice v-model="password" :messages="passwordMessages" />
        <NButton type="primary" attr-type="submit" :loading="submitting">
          {{ t('users.create.submit') }}
        </NButton>
      </NForm>
    </NCard>
  </NModal>
</template>
