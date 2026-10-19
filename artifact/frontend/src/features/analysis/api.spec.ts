import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { analysisApi } from './api'

const fetchMock = vi.fn()

function requestedUrl(): string {
  return String(fetchMock.mock.calls[0][0])
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => new Response('[]', { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('document', { cookie: '' })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('analysisApi.listAnalyses', () => {
  it('asks for nothing special by default', async () => {
    await analysisApi.listAnalyses()
    expect(requestedUrl()).toBe('/api/analyses')
  })

  it('sends allUsers only when set', async () => {
    await analysisApi.listAnalyses({ allUsers: false, ticker: 'NVDA' })
    expect(requestedUrl()).toBe('/api/analyses?ticker=NVDA')

    fetchMock.mockClear()
    await analysisApi.listAnalyses({ allUsers: true })
    expect(requestedUrl()).toBe('/api/analyses?allUsers=true')
  })
})
