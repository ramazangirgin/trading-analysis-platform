import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import tr from './locales/tr.json'

function keyPaths(value: unknown, prefix = ''): string[] {
  if (value === null || typeof value !== 'object') return [prefix]
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix ? `${prefix}.${key}` : key),
  )
}

describe('locales', () => {
  it('define the same keys in every language', () => {
    expect(keyPaths(en).sort()).toEqual(keyPaths(tr).sort())
  })
})
