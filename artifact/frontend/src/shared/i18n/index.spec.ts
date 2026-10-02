import { afterEach, describe, expect, it, vi } from 'vitest'
import { i18n, loadStoredLocale, outputLanguageFor, setLocale } from '.'

describe('setLocale', () => {
  afterEach(() => {
    setLocale('en')
    vi.unstubAllGlobals()
  })

  it('switches the global composer every component reads', () => {
    setLocale('en')
    expect(i18n.global.locale.value).toBe('en')
    expect(i18n.global.t('nav.analyses')).toBe('Analyses')

    setLocale('tr')
    expect(i18n.global.t('nav.analyses')).toBe('Analizler')
  })

  it('remembers the choice across reloads', () => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    })

    setLocale('en')
    expect(loadStoredLocale()).toBe('en')
  })

  it('falls back to English for an unknown stored value', () => {
    vi.stubGlobal('localStorage', { getItem: () => 'de', setItem: () => {} })
    expect(loadStoredLocale()).toBe('en')
  })
})

describe('outputLanguageFor', () => {
  it('starts reports in the UI language', () => {
    expect(outputLanguageFor('tr')).toBe('Turkish')
    expect(outputLanguageFor('en')).toBe('English')
  })
})
