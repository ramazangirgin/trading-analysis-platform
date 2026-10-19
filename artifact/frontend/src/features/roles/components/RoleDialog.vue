<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NAlert, NButton, NCard, NForm, NFormItem, NInput, NModal } from 'naive-ui'
import { ApiError } from '@/shared/api/error'
import type { Permission, Role, UpdateRoleRequest } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { rolesApi } from '../api'
import { permissionsLocked, renameBlocked } from '../model/roleGuards'
import PermissionChecklist from './PermissionChecklist.vue'

/** Creates a role, or with `role` edits it: only the changed fields go to the backend. */
const props = defineProps<{ show: boolean; role: Role | null }>()
const emit = defineEmits<{ close: []; saved: []; stale: [error: unknown] }>()

const { t, error: errorText } = useLabels()

const name = ref('')
const description = ref('')
const permissions = ref<Permission[]>([])
const submitting = ref(false)
const missing = ref(false)
const nameError = ref<string | null>(null)
const descriptionError = ref<string | null>(null)
const error = ref<string | null>(null)

watch(
  () => props.show,
  (show) => {
    if (!show) return
    name.value = props.role?.name ?? ''
    description.value = props.role?.description ?? ''
    permissions.value = [...(props.role?.permissions ?? [])]
    missing.value = false
    nameError.value = null
    descriptionError.value = null
    error.value = null
  },
)

const builtIn = computed(() => (props.role ? renameBlocked(props.role) : false))
const locked = computed(() => (props.role ? permissionsLocked(props.role) : false))

function sameSet(a: string[], b: string[]) {
  return a.length === b.length && a.every((item) => b.includes(item))
}

const changes = computed<UpdateRoleRequest>(() => {
  const role = props.role
  if (!role) return {}
  const body: UpdateRoleRequest = {}
  if (!builtIn.value && name.value.trim() !== role.name) body.name = name.value.trim()
  if (!builtIn.value && description.value.trim() !== (role.description ?? '')) {
    body.description = description.value.trim()
  }
  if (!locked.value && !sameSet(permissions.value, role.permissions)) {
    body.permissions = permissions.value
  }
  return body
})
const unchanged = computed(() => props.role !== null && Object.keys(changes.value).length === 0)

async function submit() {
  if (submitting.value) return
  missing.value = true
  if (!builtIn.value && !name.value.trim()) return
  submitting.value = true
  nameError.value = null
  descriptionError.value = null
  error.value = null
  try {
    if (props.role) {
      await rolesApi.update(props.role.id, { ...changes.value, version: props.role.version })
    } else {
      await rolesApi.create({
        name: name.value.trim(),
        description: description.value.trim() || undefined,
        permissions: permissions.value,
      })
    }
    emit('saved')
  } catch (e) {
    const code = e instanceof ApiError ? e.errorCode : null
    if (code === 'concurrent_update') emit('stale', e)
    else if (code === 'invalid_role_name' || code === 'role_name_taken')
      nameError.value = errorText(e)
    else if (code === 'invalid_role_description') descriptionError.value = errorText(e)
    else error.value = errorText(e)
  } finally {
    submitting.value = false
  }
}

const title = computed(() =>
  props.role
    ? t('roles.dialog.editTitle', { name: props.role.name })
    : t('roles.dialog.createTitle'),
)
</script>

<template>
  <NModal :show="show" @update:show="emit('close')">
    <NCard
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      :title="title"
      closable
      style="max-width: 560px"
      @close="emit('close')"
    >
      <NForm @submit.prevent="submit">
        <NAlert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</NAlert>
        <NAlert v-if="builtIn" type="info" :show-icon="false" style="margin-bottom: 16px">
          {{ t('roles.dialog.builtInNote') }}
        </NAlert>
        <NFormItem
          :label="t('roles.dialog.name')"
          :validation-status="
            nameError || (missing && !builtIn && !name.trim()) ? 'error' : undefined
          "
          :feedback="
            nameError ??
            (missing && !builtIn && !name.trim() ? t('roles.dialog.nameRequired') : undefined)
          "
        >
          <NInput
            v-model:value="name"
            :disabled="builtIn"
            :input-props="{ autocomplete: 'off', name: 'role-name' }"
            @update:value="nameError = null"
          />
        </NFormItem>
        <NFormItem
          :label="t('roles.dialog.description')"
          :validation-status="descriptionError ? 'error' : undefined"
          :feedback="descriptionError ?? undefined"
        >
          <NInput
            v-model:value="description"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            :disabled="builtIn"
            @update:value="descriptionError = null"
          />
        </NFormItem>
        <NFormItem :label="t('roles.dialog.permissions')">
          <div>
            <NAlert v-if="locked" type="warning" :show-icon="false" style="margin-bottom: 8px">
              {{ t('roles.dialog.adminLocked') }}
            </NAlert>
            <PermissionChecklist v-model="permissions" :disabled="locked" />
          </div>
        </NFormItem>
        <p class="note">{{ t('roles.dialog.appliesNote') }}</p>
        <NButton type="primary" attr-type="submit" :disabled="unchanged" :loading="submitting">
          {{ role ? t('roles.dialog.save') : t('roles.dialog.create') }}
        </NButton>
      </NForm>
    </NCard>
  </NModal>
</template>

<style scoped>
.note {
  margin: 0 0 12px;
  opacity: 0.75;
}
</style>
