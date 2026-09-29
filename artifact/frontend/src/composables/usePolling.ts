import { onBeforeUnmount, onMounted } from 'vue'

/** Calls `refresh` now and then every `intervalMs` while `active()` holds, and when the tab regains focus. */
export function usePolling(refresh: () => Promise<void>, active: () => boolean, intervalMs = 5000) {
  let timer: ReturnType<typeof setInterval> | undefined
  const tick = () => {
    if (active() && document.visibilityState === 'visible') void refresh()
  }
  onMounted(() => {
    void refresh()
    timer = setInterval(tick, intervalMs)
    document.addEventListener('visibilitychange', tick)
  })
  onBeforeUnmount(() => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', tick)
  })
}
