import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { settingsApi } from './api'

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

describe('settingsApi.listPresets', () => {
  it('asks for the own presets by default', async () => {
    await settingsApi.listPresets()
    expect(requestedUrl()).toBe('/api/presets')
  })

  it('does not send allUsers when it is off', async () => {
    await settingsApi.listPresets({ allUsers: false })
    expect(requestedUrl()).toBe('/api/presets')
  })

  it('sends allUsers when asked', async () => {
    await settingsApi.listPresets({ allUsers: true })
    expect(requestedUrl()).toBe('/api/presets?allUsers=true')
  })
})
