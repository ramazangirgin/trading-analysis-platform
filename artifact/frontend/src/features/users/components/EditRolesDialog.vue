<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NModal, NSpace } from 'naive-ui'
import { ApiError } from '@/shared/api/error'
import type { Role, User } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { usersApi } from '../api'
import { roleChangeBlocked } from '../model/guardRails'
import RoleSelect from './RoleSelect.vue'

const props = defineProps<{
  show: boolean
  user: User | null
  users: User[]
  roles: Role[]
  currentUserId: string
}>()
const emit = defineEmits<{ close: []; saved: []; stale: [error: unknown] }>()

const { t, error: errorText } = useLabels()

const roleIds = ref<string[]>([])
const submitting = ref(false)
const error = ref<string | null>(null)

watch(
  () => props.show,
  (show) => {
    if (!show) return
    roleIds.value = props.user?.roles.map((role) => role.id) ?? []
    error.value = null
  },
)

const own = computed(() => props.user?.id === props.currentUserId)
const blocked = computed(() =>
  props.user
    ? roleChangeBlocked(props.user, roleIds.value, props.currentUserId, props.users, props.roles)
    : null,
)
const unchanged = computed(() => {
  const current = props.user?.roles.map((role) => role.id) ?? []
  return (
    current.length === roleIds.value.length && current.every((id) => roleIds.value.includes(id))
  )
})

async function submit() {
  if (!props.user || submitting.value) return
  submitting.value = true
  error.value = null
  try {
    await usersApi.update(props.user.id, { roleIds: roleIds.value, version: props.user.version })
    emit('saved')
  } catch (e) {
    if (e instanceof ApiError && e.errorCode === 'concurrent_update') emit('stale', e)
    else error.value = errorText(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal :show="show" @update:show="emit('close')">
    <NCard
      :title="t('users.editRoles.title', { username: user?.username ?? '' })"
      closable
      style="max-width: 480px"
      @close="emit('close')"
    >
      <NSpace vertical :size="12">
        <NAlert v-if="error" type="error">{{ error }}</NAlert>
        <RoleSelect v-model="roleIds" :roles="roles" />
        <p class="note">
          {{ own ? t('users.editRoles.ownSession') : t('users.editRoles.sessionsEnd') }}
        </p>
        <NAlert v-if="blocked" type="warning" :show-icon="false">
          {{ t(`users.guard.${blocked}`) }}
        </NAlert>
        <NButton
          type="primary"
          :disabled="blocked !== null || unchanged || roleIds.length === 0"
          :loading="submitting"
          @click="submit"
        >
          {{ t('users.editRoles.submit') }}
        </NButton>
      </NSpace>
    </NCard>
  </NModal>
</template>

<style scoped>
.note {
  margin: 0;
  opacity: 0.75;
}
</style>
