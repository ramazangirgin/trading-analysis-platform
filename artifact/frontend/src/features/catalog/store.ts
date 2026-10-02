import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Catalog } from '@/shared/api/types'
import { catalogApi } from './api'

/** The runner's catalog, loaded once per session: it only changes with the TradingAgents version. */
export const useCatalogStore = defineStore('catalog', () => {
  const catalog = ref<Catalog | null>(null)
  const error = ref<unknown>(null)
  let pending: Promise<void> | null = null

  function load(): Promise<void> {
    if (catalog.value) return Promise.resolve()
    pending ??= catalogApi
      .getCatalog()
      .then((value) => {
        catalog.value = value
        error.value = null
      })
      .catch((e) => {
        error.value = e
      })
      .finally(() => {
        pending = null
      })
    return pending
  }

  return { catalog, error, load }
})
