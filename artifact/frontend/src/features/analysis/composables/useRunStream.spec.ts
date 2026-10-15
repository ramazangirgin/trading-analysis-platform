import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref, type EffectScope } from 'vue'
import { ApiError } from '@/shared/api/error'
import type { RunEvent } from '@/shared/api/types'

vi.mock('../api', () => ({
  analysisApi: {
    eventsUrl: vi.fn((id: string) => `/api/analyses/${encodeURIComponent(id)}/events`),
    getAnalysis: vi.fn(),
  },
}))

import { analysisApi } from '../api'
import { useRunStream } from './useRunStream'

/** The browser's view of the stream: connection state, handlers and, for the tests, the events. */
class FakeEventSource {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2
  static instances: FakeEventSource[] = []

  readonly url: string
  readyState = FakeEventSource.CONNECTING
  closed = false
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  private readonly endListeners: (() => void)[] = []

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: () => void): void {
    if (type === 'end') this.endListeners.push(listener)
  }

  close(): void {
    this.readyState = FakeEventSource.CLOSED
    this.closed = true
  }

  /** The server side of the fake: the connection opens. */
  connect(): void {
    this.readyState = FakeEventSource.OPEN
    this.onopen?.()
  }

  /** The server side of the fake: one event of the stream. */
  send(type: string, event: RunEvent): void {
    if (type === 'end') this.endListeners.forEach((listener) => listener())
    else this.onmessage?.({ data: JSON.stringify(event) })
  }

  /** The server side of the fake: an error, with the state the source ends up in. */
  fail(readyState: number): void {
    this.readyState = readyState
    this.onerror?.()
  }
}

let seq = 0
const event = (type: string, payload: Record<string, unknown>): RunEvent => ({
  seq: ++seq,
  timestamp: '2026-10-15T10:00:00Z',
  type,
  payload,
})

let scope: EffectScope | undefined

function start(id = 'run-1') {
  scope = effectScope()
  return scope.run(() => useRunStream(ref(id)))!
}

describe('useRunStream', () => {
  beforeEach(() => {
    vi.stubGlobal('EventSource', FakeEventSource)
    FakeEventSource.instances = []
    vi.resetAllMocks()
  })

  afterEach(() => {
    scope?.stop()
    vi.unstubAllGlobals()
  })

  it('applies events to the view and closes the source on end', () => {
    const { view, connected, ended } = start()
    const source = FakeEventSource.instances[0]!
    expect(analysisApi.eventsUrl).toHaveBeenCalledWith('run-1')
    expect(source.url).toBe('/api/analyses/run-1/events')

    source.connect()
    expect(connected.value).toBe(true)

    source.send(
      'message',
      event('agent_status', { agent: 'Market Analyst', status: 'in_progress' }),
    )
    expect(view.agents).toEqual({ 'Market Analyst': 'in_progress' })
    expect(ended.value).toBe(false)

    source.send('end', event('run_finished', { status: 'completed' }))
    expect(ended.value).toBe(true)
    expect(source.closed).toBe(true)
    expect(connected.value).toBe(false)
  })

  it('makes no API call while the source reconnects', () => {
    const { connected } = start()
    const source = FakeEventSource.instances[0]!
    source.connect()
    source.fail(FakeEventSource.CONNECTING)
    expect(connected.value).toBe(false)
    expect(source.closed).toBe(false)
    expect(analysisApi.getAnalysis).not.toHaveBeenCalled()
  })

  it('probes the backend once when the source closes without an end', async () => {
    vi.mocked(analysisApi.getAnalysis).mockRejectedValue(
      new ApiError(401, 'unauthorized', {}, 'unauthorized'),
    )
    const { connected, ended } = start()
    const source = FakeEventSource.instances[0]!
    source.fail(FakeEventSource.CLOSED)
    source.fail(FakeEventSource.CLOSED)
    await vi.waitFor(() => expect(analysisApi.getAnalysis).toHaveBeenCalledTimes(1))
    expect(analysisApi.getAnalysis).toHaveBeenCalledWith('run-1')
    expect(ended.value).toBe(false)
    expect(connected.value).toBe(false)
    expect(source.closed).toBe(true)
  })

  it('makes no API call for an error after the run ended', () => {
    const { ended } = start()
    const source = FakeEventSource.instances[0]!
    source.send('end', event('run_finished', { status: 'completed' }))
    expect(ended.value).toBe(true)
    source.fail(FakeEventSource.CLOSED)
    expect(analysisApi.getAnalysis).not.toHaveBeenCalled()
  })

  it('closes the source when the scope stops', () => {
    start()
    const source = FakeEventSource.instances[0]!
    scope!.stop()
    expect(source.closed).toBe(true)
  })
})
