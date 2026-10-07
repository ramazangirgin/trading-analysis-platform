import { createI18n } from 'vue-i18n'
import en from './locales/en.json'
import tr from './locales/tr.json'

export type MessageSchema = typeof en
export const SUPPORTED_LOCALES = ['en', 'tr'] as const
export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

// English by default; the viewer's choice is remembered per browser.
export const DEFAULT_LOCALE: AppLocale = 'en'
const STORAGE_KEY = 'tap.locale'

export function isAppLocale(value: unknown): value is AppLocale {
  return SUPPORTED_LOCALES.includes(value as AppLocale)
}

export function loadStoredLocale(): AppLocale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isAppLocale(stored) ? stored : DEFAULT_LOCALE
  } catch {
    return DEFAULT_LOCALE
  }
}

export function storeLocale(locale: AppLocale): void {
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // Storage can be unavailable (private mode, blocked site data); the choice then lasts the session.
  }
}

export const messages: Record<AppLocale, MessageSchema> = { en, tr }

export const i18n = createI18n<[MessageSchema], AppLocale, false>({
  legacy: false,
  locale: loadStoredLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages,
})

/** Switches the whole UI (the global composer every `useI18n()` reads) and remembers the choice. */
export function setLocale(locale: AppLocale): void {
  i18n.global.locale.value = locale
  storeLocale(locale)
}

/** The report language a new analysis starts with: the one the UI is shown in. */
export function outputLanguageFor(locale: string): string {
  return locale === 'tr' ? 'Turkish' : 'English'
}
