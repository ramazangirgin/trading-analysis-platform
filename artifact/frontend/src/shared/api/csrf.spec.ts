import { describe, expect, it } from 'vitest'
import { csrfToken } from './csrf'

describe('csrfToken', () => {
  it('is null without any cookie', () => {
    expect(csrfToken('')).toBeNull()
  })

  it('is null when only other cookies exist', () => {
    expect(csrfToken('a=1; JSESSIONID=abc')).toBeNull()
  })

  it('reads the token among other cookies', () => {
    expect(csrfToken('a=1; XSRF-TOKEN=tok; b=2')).toBe('tok')
  })

  it('decodes an encoded value', () => {
    expect(csrfToken('XSRF-TOKEN=a%2Fb%3D%3D')).toBe('a/b==')
  })
})
