import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { previewApi } from './api'

const fetchMock = vi.fn()

function requestedUrl(): string {
  return String(fetchMock.mock.calls[0][0])
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => new Response('{}', { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('document', { cookie: '' })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('previewApi', () => {
  it('lists a page with its size in the query', async () => {
    await previewApi.list(2, 12)
    expect(requestedUrl()).toBe('/api/public/analyses?page=2&size=12')
  })

  it('gets one preview by its encoded ID', async () => {
    await previewApi.get('a/b')
    expect(requestedUrl()).toBe('/api/public/analyses/a%2Fb')
  })
})
