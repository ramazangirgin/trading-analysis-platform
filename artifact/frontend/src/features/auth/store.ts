import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '@/shared/api/error'
import type { CurrentUser, Permission } from '@/shared/api/types'
import { authApi } from './api'

/** The logged-in user and their permissions: the one place the UI asks who may do what. */
export const useSessionStore = defineStore('session', () => {
  const user = ref<CurrentUser | null>(null)
  const loaded = ref(false)
  let pending: Promise<void> | null = null

  const username = computed(() => user.value?.username ?? null)
  const permissions = computed(() => new Set(user.value?.permissions ?? []))
  const mustChangePassword = computed(() => user.value?.mustChangePassword ?? false)

  function can(permission: Permission): boolean {
    return permissions.value.has(permission)
  }

  /** Asks the backend once who is logged in; no session (401) is an answer, not an error. */
  function load(): Promise<void> {
    if (loaded.value) return Promise.resolve()
    pending ??= authApi
      .me()
      .then((value) => {
        user.value = value
        loaded.value = true
      })
      .catch((e) => {
        user.value = null
        if (e instanceof ApiError && e.status === 401) {
          loaded.value = true
          return
        }
        throw e
      })
      .finally(() => {
        pending = null
      })
    return pending
  }

  async function login(name: string, password: string): Promise<void> {
    user.value = await authApi.login(name, password)
    loaded.value = true
  }

  /** The backend answers with the user, whose flag is cleared now. */
  async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
    user.value = await authApi.changePassword(currentPassword, newPassword)
  }

  /** The backend refused a request until the password is changed: the user's flag was out of date. */
  function requirePasswordChange(): void {
    if (user.value) user.value = { ...user.value, mustChangePassword: true }
  }

  /** A full page load drops every in-memory store, so nothing protected survives the logout. */
  async function logout(): Promise<void> {
    await authApi.logout()
    window.location.assign('/login')
  }

  function clear(): void {
    user.value = null
  }

  return {
    user,
    loaded,
    username,
    permissions,
    mustChangePassword,
    can,
    load,
    login,
    changePassword,
    requirePasswordChange,
    logout,
    clear,
  }
})
