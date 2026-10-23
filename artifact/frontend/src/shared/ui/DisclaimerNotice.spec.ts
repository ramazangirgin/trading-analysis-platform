import { afterEach, describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { i18n, messages, setLocale } from '@/shared/i18n'
import DisclaimerNotice from './DisclaimerNotice.vue'

async function render(props: { locale?: 'en' | 'tr'; compact?: boolean } = {}) {
  const app = createSSRApp({ render: () => h(DisclaimerNotice, props) })
  app.use(i18n)
  return renderToString(app)
}

describe('DisclaimerNotice', () => {
  afterEach(() => setLocale('en'))

  it('follows the screen language by default', async () => {
    setLocale('tr')
    expect(await render()).toContain(messages.tr.app.disclaimer)
    setLocale('en')
    expect(await render()).toContain(messages.en.app.disclaimer)
  })

  it('shows the given locale whatever the screen language is', async () => {
    setLocale('en')
    const html = await render({ locale: 'tr' })
    expect(html).toContain(messages.tr.app.disclaimer)
    expect(html).not.toContain(messages.en.app.disclaimer)
  })

  it('is a note, compact on request', async () => {
    const html = await render({ compact: true })
    expect(html).toContain('role="note"')
    expect(html).toContain('compact')
  })
})
