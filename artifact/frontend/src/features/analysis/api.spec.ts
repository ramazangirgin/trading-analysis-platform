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

  it('sends the scope only when it is not MINE', async () => {
    await analysisApi.listAnalyses({ scope: 'MINE', ticker: 'NVDA' })
    expect(requestedUrl()).toBe('/api/analyses?ticker=NVDA')

    fetchMock.mockClear()
    await analysisApi.listAnalyses({ scope: 'SHARED' })
    expect(requestedUrl()).toBe('/api/analyses?scope=SHARED')

    fetchMock.mockClear()
    await analysisApi.listAnalyses({ scope: 'ALL' })
    expect(requestedUrl()).toBe('/api/analyses?scope=ALL')
  })
})

describe('analysisApi.changeVisibility', () => {
  it('puts the visibility and the version', async () => {
    await analysisApi.changeVisibility('abc', { visibility: 'PUBLIC', version: 3 })
    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/analyses/abc/visibility')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ visibility: 'PUBLIC', version: 3 }))
  })
})
