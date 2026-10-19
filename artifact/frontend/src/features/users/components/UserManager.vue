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
import type { Role, User } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { usersApi } from '../api'
import { disableBlocked } from '../model/guardRails'
import { userStatus } from '../model/userStatus'
import CreateUserDialog from './CreateUserDialog.vue'
import EditRolesDialog from './EditRolesDialog.vue'
import OneTimePasswordDialog from './OneTimePasswordDialog.vue'
import ResetPasswordDialog from './ResetPasswordDialog.vue'

/** Admin → Users: every user with their roles and status; with `canManage` also the changes. */
const props = defineProps<{ canManage: boolean; currentUserId: string }>()
const { t, error: errorLabel, dateTime } = useLabels()
const message = useMessage()
const users = ref<User[]>([])
const roles = ref<Role[]>([])
const busy = ref<string | null>(null)
const failure = ref<unknown>(null)

const creating = ref(false)
const editing = ref<User | null>(null)
const resetting = ref<User | null>(null)
const issued = ref<{ username: string; password: string | null } | null>(null)

async function load() {
  try {
    ;[users.value, roles.value] = await Promise.all([usersApi.list(), usersApi.listRoles()])
    failure.value = null
  } catch (e) {
    failure.value = e
  }
}

async function failed(e: unknown) {
  message.error(errorLabel(e))
  if (e instanceof ApiError && e.errorCode === 'concurrent_update') await load()
}

async function setEnabled(user: User, enabled: boolean) {
  busy.value = user.id
  try {
    await usersApi.update(user.id, { enabled, version: user.version })
    await load()
  } catch (e) {
    await failed(e)
  } finally {
    busy.value = null
  }
}

async function unlock(user: User) {
  busy.value = user.id
  try {
    await usersApi.unlock(user.id)
    await load()
  } catch (e) {
    await failed(e)
  } finally {
    busy.value = null
  }
}

async function created(user: User, oneTimePassword: string | null) {
  creating.value = false
  issued.value = { username: user.username, password: oneTimePassword }
  await load()
}

async function passwordReset(user: User, oneTimePassword: string | null) {
  resetting.value = null
  issued.value = { username: user.username, password: oneTimePassword }
  await load()
}

async function rolesSaved() {
  editing.value = null
  await load()
}

async function rolesStale(e: unknown) {
  editing.value = null
  await failed(e)
}

function disableAction(user: User) {
  const reason = disableBlocked(user, props.currentUserId, users.value, roles.value)
  const label = () => t('users.actions.disable')
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
        default: () => t(`users.guard.${reason}`),
      },
    )
  }
  return h(
    NPopconfirm,
    { onPositiveClick: () => setEnabled(user, false) },
    {
      trigger: () =>
        h(
          NButton,
          { size: 'small', secondary: true, type: 'warning', loading: busy.value === user.id },
          label,
        ),
      default: () => t('users.disableConfirm', { username: user.username }),
    },
  )
}

const columns = computed<DataTableColumns<User>>(() => [
  { title: t('users.columns.username'), key: 'username' },
  {
    title: t('users.columns.roles'),
    key: 'roles',
    render: (user) =>
      h(NSpace, { size: 4 }, () =>
        user.roles.map((role) => h(NTag, { size: 'small', bordered: false }, () => role.name)),
      ),
  },
  {
    title: t('users.columns.status'),
    key: 'status',
    render: (user) => {
      const status = userStatus(user)
      return h(NSpace, { size: 4 }, () => [
        h(
          NTag,
          {
            size: 'small',
            bordered: false,
            type: status === 'active' ? 'success' : status === 'locked' ? 'warning' : 'default',
          },
          () => t(`users.status.${status}`),
        ),
        user.mustChangePassword
          ? h(NTag, { size: 'small', bordered: false, type: 'info' }, () => t('users.oneTimeTag'))
          : null,
      ])
    },
  },
  {
    title: t('users.columns.lastLogin'),
    key: 'lastLoginAt',
    render: (user) => (user.lastLoginAt ? dateTime(user.lastLoginAt) : t('users.neverLoggedIn')),
  },
  {
    title: t('users.columns.created'),
    key: 'createdAt',
    render: (user) => dateTime(user.createdAt),
  },
  ...(props.canManage
    ? [
        {
          title: t('users.columns.actions'),
          key: 'actions',
          render: (user: User) =>
            h(NSpace, { size: 4, wrap: false }, () => [
              h(NButton, { size: 'small', onClick: () => (editing.value = user) }, () =>
                t('users.actions.editRoles'),
              ),
              user.enabled
                ? disableAction(user)
                : h(
                    NButton,
                    {
                      size: 'small',
                      secondary: true,
                      loading: busy.value === user.id,
                      onClick: () => setEnabled(user, true),
                    },
                    () => t('users.actions.enable'),
                  ),
              h(NButton, { size: 'small', onClick: () => (resetting.value = user) }, () =>
                t('users.actions.resetPassword'),
              ),
              user.locked
                ? h(
                    NButton,
                    {
                      size: 'small',
                      loading: busy.value === user.id,
                      onClick: () => unlock(user),
                    },
                    () => t('users.actions.unlock'),
                  )
                : null,
            ]),
        },
      ]
    : []),
])

onMounted(load)
</script>

<template>
  <NSpace vertical :size="12">
    <NAlert v-if="failure" type="error" :title="t('users.loadFailed')">
      {{ errorLabel(failure) }}
    </NAlert>
    <div v-if="canManage">
      <NButton type="primary" @click="creating = true">{{ t('users.newUser') }}</NButton>
    </div>
    <NDataTable
      :columns="columns"
      :data="users"
      :row-key="(user: User) => user.id"
      :bordered="false"
      size="small"
      :scroll-x="900"
    />
    <CreateUserDialog
      :show="creating"
      :roles="roles"
      @close="creating = false"
      @created="created"
    />
    <EditRolesDialog
      :show="editing !== null"
      :user="editing"
      :users="users"
      :roles="roles"
      :current-user-id="currentUserId"
      @close="editing = null"
      @saved="rolesSaved"
      @stale="rolesStale"
    />
    <ResetPasswordDialog
      :show="resetting !== null"
      :user="resetting"
      :current-user-id="currentUserId"
      @close="resetting = null"
      @reset="passwordReset"
    />
    <OneTimePasswordDialog
      :show="issued !== null"
      :username="issued?.username ?? ''"
      :password="issued?.password ?? null"
      @close="issued = null"
    />
  </NSpace>
</template>
