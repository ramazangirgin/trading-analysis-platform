import { afterEach, describe, expect, it } from 'vitest'
import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { i18n, setLocale } from '@/shared/i18n'
import { ApiError } from '@/shared/api/error'
import { useLabels } from '@/shared/composables/useLabels'

/** What LoginForm shows for a failed login: `useLabels().error` of the error, in an alert. */
async function loginErrorText(error: ApiError): Promise<string> {
  const Probe = defineComponent({
    setup() {
      const { error: errorText } = useLabels()
      return () => h('p', errorText(error))
    },
  })
  const app = createSSRApp(Probe).use(i18n)
  return renderToString(app)
}

describe('login error message', () => {
  afterEach(() => setLocale('en'))

  const tooMany = new ApiError(429, 'too_many_login_attempts', {}, 'Too many')

  it('tells the user that too many logins failed from this address', async () => {
    expect(await loginErrorText(tooMany)).toContain('Too many failed logins from this address.')
  })

  it('is translated', async () => {
    setLocale('tr')
    expect(await loginErrorText(tooMany)).toContain('çok fazla başarısız giriş')
  })
})
