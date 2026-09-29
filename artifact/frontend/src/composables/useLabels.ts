import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/client'

/** Upstream agent name ("Market Analyst") -> message key ("market_analyst"). */
export const agentKey = (agent: string): string => agent.toLowerCase().replace(/ /g, '_')

/** Display helpers shared by the pages: translations, locale-aware numbers, dates and durations. */
export function useLabels() {
  const { t, te, d, n, locale } = useI18n()

  const agent = (name: string): string =>
    te(`agents.${agentKey(name)}`) ? t(`agents.${agentKey(name)}`) : name

  const error = (e: unknown): string => {
    if (e instanceof ApiError) {
      return te(`errors.${e.errorCode}`) ? t(`errors.${e.errorCode}`, e.params) : e.message
    }
    return t('errors.unexpected_error')
  }

  const errorCode = (code: string | null, fallback: string | null): string =>
    code && te(`errors.${code}`) ? t(`errors.${code}`) : (fallback ?? t('errors.unexpected_error'))

  const dateTime = (iso: string | null): string =>
    iso
      ? new Date(iso).toLocaleString(locale.value, { dateStyle: 'short', timeStyle: 'short' })
      : '—'

  const duration = (ms: number | null): string => {
    if (ms === null || ms <= 0) return '—'
    const seconds = Math.round(ms / 1000)
    const minutes = Math.floor(seconds / 60)
    return minutes > 0 ? `${minutes}m ${seconds % 60}s` : `${seconds}s`
  }

  const integer = (value: number | null | undefined): string =>
    value === null || value === undefined ? '—' : new Intl.NumberFormat(locale.value).format(value)

  const usd = (value: number | null | undefined): string =>
    value === null || value === undefined
      ? '—'
      : new Intl.NumberFormat(locale.value, {
          style: 'currency',
          currency: 'USD',
          maximumFractionDigits: 4,
        }).format(value)

  return { t, d, n, agent, error, errorCode, dateTime, duration, integer, usd }
}
