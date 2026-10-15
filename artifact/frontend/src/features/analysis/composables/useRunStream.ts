import { onScopeDispose, reactive, ref, watch, type Ref } from 'vue'
import type { RunEvent } from '@/shared/api/types'
import { applyRunEvent, emptyRunView, type RunView } from '../model/runView'
import { analysisApi } from '../api'

/**
 * Follows a run's event stream. The browser's EventSource reconnects by itself and sends
 * Last-Event-ID, so the server resumes where the stream broke; `applyRunEvent` drops anything
 * already seen. The server sends `end` once the run is over, and the stream is closed then.
 * The server also closes the stream when the session ends (logout, idle timeout, a password
 * change in another session): the reconnect gets 401 and the source gives up for good. The
 * composable then probes the backend once with `getAnalysis`; the 401 there goes through the
 * client's handler, which the app wires to the login page, and the stream stays closed.
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
    source = new EventSource(analysisApi.eventsUrl(id.value))
    source.onopen = () => (connected.value = true)
    source.onerror = () => {
      connected.value = false
      // A CLOSED source never reconnects; without a session the reconnect answered 401.
      if (source?.readyState === EventSource.CLOSED && !ended.value) {
        void analysisApi.getAnalysis(id.value).catch(() => undefined)
        close()
      }
    }
    source.onmessage = (message: MessageEvent<string>) => {
      applyRunEvent(view, JSON.parse(message.data) as RunEvent)
    }
    source.addEventListener('end', () => {
      ended.value = true
      close()
    })
  }

  watch(id, open, { immediate: true })
  onScopeDispose(close)

  return { view, connected, ended }
}
