import { describe, expect, it } from 'vitest'
import { createSSRApp, h, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { renderToString } from 'vue/server-renderer'
import { i18n } from '@/shared/i18n'
import LockedSection from './LockedSection.vue'
import PreviewCard from './PreviewCard.vue'
import SignUpCallToAction from './SignUpCallToAction.vue'

async function render(component: Component, props: Record<string, unknown>) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: { render: () => null } },
      { path: '/preview/:id', name: 'preview', component: { render: () => null } },
    ],
  })
  const app = createSSRApp({ render: () => h(component, props) })
  app.use(i18n).use(router)
  await router.push('/login')
  return renderToString(app)
}

describe('LockedSection', () => {
  it('renders the title and nothing else of its props', async () => {
    const html = await render(LockedSection, {
      title: 'Sentiment report',
      text: 'secret body of the report',
    })
    expect(html).toContain('Sentiment report')
    expect(html).not.toContain('secret body')
  })
})

describe('PreviewCard', () => {
  it('links to the preview page of the analysis', async () => {
    const html = await render(PreviewCard, {
      analysis: {
        id: 'abc-1',
        ticker: 'NVDA',
        analysisDate: '2026-01-02',
        rating: 'BUY',
        excerpt: 'The start of the decision',
      },
    })
    expect(html).toContain('href="/preview/abc-1"')
    expect(html).toContain('NVDA')
  })
})

describe('SignUpCallToAction', () => {
  it('links to the login route that returns to the full analysis', async () => {
    const html = await render(SignUpCallToAction, { analysisId: 'abc-1' })
    expect(html).toContain('href="/login?redirect=/analyses/abc-1"')
  })
})
