<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NAlert,
  NButton,
  NDataTable,
  NPopconfirm,
  NSpace,
  NTag,
  NTooltip,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { ApiError } from '@/shared/api/error'
import type { Role } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { rolesApi } from '../api'
import { deleteBlocked } from '../model/roleGuards'
import RoleDialog from './RoleDialog.vue'

/** Admin → Roles: every role with its permissions and users; create, edit and delete custom ones. */
const { t, error: errorLabel } = useLabels()
const message = useMessage()
const roles = ref<Role[]>([])
const failure = ref<unknown>(null)
const busy = ref<string | null>(null)

const dialogOpen = ref(false)
const editing = ref<Role | null>(null)

async function load() {
  try {
    roles.value = await rolesApi.list()
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

async function failed(e: unknown) {
  message.error(errorLabel(e))
  if (e instanceof ApiError && e.errorCode === 'concurrent_update') await load()
}

function openCreate() {
  editing.value = null
  dialogOpen.value = true
}

function openEdit(role: Role) {
  editing.value = role
  dialogOpen.value = true
}

async function saved() {
  dialogOpen.value = false
  await load()
}

async function stale(e: unknown) {
  dialogOpen.value = false
  await failed(e)
}

async function remove(role: Role) {
  busy.value = role.id
  try {
    await rolesApi.remove(role.id)
    await load()
  } catch (e) {
    await failed(e)
  } finally {
    busy.value = null
  }
}

function deleteAction(role: Role) {
  const reason = deleteBlocked(role)
  const label = () => t('roles.actions.delete')
  if (reason) {
    // The span takes the hover: a disabled button swallows mouse events.
    return h(
      NTooltip,
      {},
      {
        trigger: () =>
          h('span', { style: 'display: inline-block' }, [
            h(
              NButton,
              { size: 'small', secondary: true, disabled: true, style: 'pointer-events: none' },
              label,
            ),
          ]),
        default: () => t(`roles.guard.${reason}`, { userCount: role.userCount }),
      },
    )
  }
  return h(
    NPopconfirm,
    { onPositiveClick: () => remove(role) },
    {
      trigger: () =>
        h(
          NButton,
          { size: 'small', secondary: true, type: 'error', loading: busy.value === role.id },
          label,
        ),
      default: () => t('roles.deleteConfirm', { name: role.name }),
    },
  )
}

const columns = computed<DataTableColumns<Role>>(() => [
  {
    title: t('roles.columns.name'),
    key: 'name',
    minWidth: 160,
    render: (role) =>
      h(NSpace, { size: 6, wrap: false }, () => [
        h('span', role.name),
        role.builtIn ? h(NTag, { size: 'small', bordered: false }, () => t('roles.builtIn')) : null,
      ]),
  },
  {
    title: t('roles.columns.description'),
    key: 'description',
    render: (role) => role.description ?? '—',
  },
  {
    title: t('roles.columns.permissions'),
    key: 'permissions',
    render: (role) =>
      role.permissions.length === 0
        ? t('roles.noPermissions')
        : h(NSpace, { size: 4 }, () =>
            role.permissions.map((permission) =>
              h(NTag, { size: 'small', bordered: false }, () => permission),
            ),
          ),
  },
  { title: t('roles.columns.users'), key: 'userCount', minWidth: 70 },
  {
    title: t('roles.columns.actions'),
    key: 'actions',
    render: (role) =>
      h(NSpace, { size: 4, wrap: false }, () => [
        h(NButton, { size: 'small', onClick: () => openEdit(role) }, () => t('roles.actions.edit')),
        deleteAction(role),
      ]),
  },
])

onMounted(load)
</script>

<template>
  <NSpace vertical :size="12">
    <NAlert v-if="failure" type="error" :title="t('roles.loadFailed')">
      {{ errorLabel(failure) }}
    </NAlert>
    <div>
      <NButton type="primary" @click="openCreate">{{ t('roles.newRole') }}</NButton>
    </div>
    <NDataTable
      :columns="columns"
      :data="roles"
      :row-key="(role: Role) => role.id"
      :bordered="false"
      size="small"
      :scroll-x="900"
    />
    <RoleDialog
      :show="dialogOpen"
      :role="editing"
      @close="dialogOpen = false"
      @saved="saved"
      @stale="stale"
    />
  </NSpace>
</template>
