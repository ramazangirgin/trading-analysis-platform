import { afterEach, describe, expect, it } from 'vitest'
import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { i18n, setLocale } from '@/shared/i18n'
import { ApiError } from '@/shared/api/error'
import { useLabels } from './useLabels'

/** The text `useLabels().error` gives for an error, as the login form shows it in its alert. */
async function errorTextOf(error: ApiError): Promise<string> {
  const Probe = defineComponent({
    setup() {
      const { error: errorText } = useLabels()
      return () => h('p', errorText(error))
    },
  })
  const app = createSSRApp(Probe).use(i18n)
  return renderToString(app)
}

describe('useLabels error for too_many_login_attempts', () => {
  afterEach(() => setLocale('en'))

  const tooMany = new ApiError(429, 'too_many_login_attempts', {}, 'Too many')

  it('tells the user that too many logins failed from this address', async () => {
    expect(await errorTextOf(tooMany)).toContain('Too many failed logins from this address.')
  })

  it('is translated', async () => {
    setLocale('tr')
    expect(await errorTextOf(tooMany)).toContain('çok fazla başarısız giriş')
  })
})
