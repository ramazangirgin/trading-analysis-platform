import { onBeforeUnmount, reactive, ref, watch, type Ref } from 'vue'
import { api, type RunEvent } from '@/api/client'
import { applyRunEvent, emptyRunView, type RunView } from '@/domain/runView'

/**
 * Follows a run's event stream. The browser's EventSource reconnects by itself and sends
 * Last-Event-ID, so the server resumes where the stream broke; `applyRunEvent` drops anything
 * already seen. The server sends `end` once the run is over, and the stream is closed then.
 */
export function useRunStream(id: Ref<string>) {
  const view = reactive<RunView>(emptyRunView())
  const connected = ref(false)
  const ended = ref(false)
  let source: EventSource | null = null

  function close() {
    source?.close()
    source = null
    connected.value = false
  }

  function open() {
    close()
    Object.assign(view, emptyRunView())
    ended.value = false
    source = new EventSource(api.eventsUrl(id.value))
    source.onopen = () => (connected.value = true)
    source.onerror = () => (connected.value = false)
    source.onmessage = (message: MessageEvent<string>) => {
      applyRunEvent(view, JSON.parse(message.data) as RunEvent)
    }
    source.addEventListener('end', () => {
      ended.value = true
      close()
    })
  }

  watch(id, open, { immediate: true })
  onBeforeUnmount(close)

  return { view, connected, ended }
}
