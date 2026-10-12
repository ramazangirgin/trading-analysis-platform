import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { onPasswordChangeRequired, onUnauthorized, request } from './client'
import { ApiError } from './error'

const fetchMock = vi.fn()

function respond(status: number, body: unknown = {}): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), { status })
}

function sentHeaders(): Headers {
  return new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers)
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(respond(200, {}))
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=tok%3D' })
  onUnauthorized(() => {})
  onPasswordChangeRequired(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('request CSRF header', () => {
  it.each(['POST', 'PUT', 'DELETE'])('is sent on %s', async (method) => {
    await request('/api/x', { method })
    expect(sentHeaders().get('X-XSRF-TOKEN')).toBe('tok=')
  })

  it('is not sent on GET', async () => {
    await request('/api/x')
    expect(sentHeaders().get('X-XSRF-TOKEN')).toBeNull()
  })

  it('is left out without the cookie', async () => {
    vi.stubGlobal('document', { cookie: '' })
    await request('/api/x', { method: 'POST' })
    expect(sentHeaders().get('X-XSRF-TOKEN')).toBeNull()
  })
})

describe('request headers', () => {
  it('merges the caller headers', async () => {
    await request('/api/x', { method: 'POST', body: '{}', headers: { 'X-Custom': 'a' } })
    const headers = sentHeaders()
    expect(headers.get('X-Custom')).toBe('a')
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Content-Type')).toBe('application/json')
  })
})

describe('request 401', () => {
  it('calls the handler and still throws the ApiError', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    fetchMock.mockResolvedValue(respond(401, { errorCode: 'unauthorized' }))
    const error = (await request('/api/x').catch((e: unknown) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(401)
    expect(error.errorCode).toBe('unauthorized')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not call the handler with skipUnauthorized', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    fetchMock.mockResolvedValue(respond(401, { errorCode: 'bad_credentials' }))
    await expect(request('/api/x', undefined, { skipUnauthorized: true })).rejects.toBeInstanceOf(
      ApiError,
    )
    expect(handler).not.toHaveBeenCalled()
  })

  it('does not call the session-ended handler on a 403', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    fetchMock.mockResolvedValue(respond(403, { errorCode: 'forbidden' }))
    await expect(request('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()
  })
})

describe('request 403 password_change_required', () => {
  it('calls the handler and still throws the ApiError', async () => {
    const handler = vi.fn()
    const unauthorized = vi.fn()
    onPasswordChangeRequired(handler)
    onUnauthorized(unauthorized)
    fetchMock.mockResolvedValue(respond(403, { errorCode: 'password_change_required' }))
    const error = (await request('/api/x').catch((e: unknown) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(403)
    expect(error.errorCode).toBe('password_change_required')
    expect(handler).toHaveBeenCalledTimes(1)
    expect(unauthorized).not.toHaveBeenCalled()
  })

  it('is not called for another 403', async () => {
    const handler = vi.fn()
    onPasswordChangeRequired(handler)
    fetchMock.mockResolvedValue(respond(403, { errorCode: 'forbidden' }))
    await expect(request('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()
  })

  it('is not called for a 401', async () => {
    const handler = vi.fn()
    onPasswordChangeRequired(handler)
    fetchMock.mockResolvedValue(respond(401, { errorCode: 'unauthorized' }))
    await expect(request('/api/x')).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()
  })
})
