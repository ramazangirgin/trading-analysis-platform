import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'
import { csrfHeaders, DECISION_TEXT } from '../tests/support'

// README, "5. Watch it and read the results": a replayed run, live and finished.
test('a running analysis', async ({ page, request }) => {
  const started = await request.post('/api/analyses', {
    headers: await csrfHeaders(request),
    data: {
      ticker: 'NVDA',
      tradeDate: '2026-09-29',
      analysts: ['MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS'],
      llmProvider: 'openai',
      deepThinkLlm: 'gpt-5.4-mini',
      quickThinkLlm: 'gpt-5.4-mini',
    },
  })
  expect(started.ok(), await started.text()).toBe(true)
  const { id } = (await started.json()) as { id: string }

  await page.goto(`/analyses/${id}`)
  await expect(page.getByText('Running').first()).toBeVisible()
  // The pipeline and the feed fill in while the recording plays (about nine seconds).
  await expect(page.getByText('Market Analyst').first()).toBeVisible()
  await expect(page.getByText('Connection lost')).toHaveCount(0)
  await expect(page.getByText('Waiting for events')).toHaveCount(0)
  await shot(page, 'run-live')
})

test('a finished analysis', async ({ page, request }) => {
  const id = await replayedRun(request, 'MU', '2026-09-29')
  await page.goto(`/analyses/${id}`)
  await expect(page.getByText(DECISION_TEXT).first()).toBeVisible()
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()
  await shot(page, 'run-decision')

  const tab = (name: string) => page.locator('.n-tabs-tab', { hasText: name })
  const panel = page.locator('.n-tabs-pane-wrapper')

  await tab('Reports').click()
  await expect(page.locator('.n-collapse-item__header').first()).toBeVisible()
  await shot(page, 'run-reports', panel)

  await tab('Debates').click()
  await expect(panel).toBeVisible()
  await shot(page, 'run-debates', panel)

  await tab('Stats').click()
  await expect(panel).toBeVisible()
  await shot(page, 'run-stats', panel)
})
