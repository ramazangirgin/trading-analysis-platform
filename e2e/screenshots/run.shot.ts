import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'

const PAGE = { x: 0, y: 0, width: 1440 }

// README, "5. Start an analysis": a finished analysis, its pipeline and the decision card.
test('finished analysis', async ({ page, request }) => {
  const id = await replayedRun(request, 'MU', '2026-09-29')
  await page.goto(`/analyses/${id}`)
  await expect(page.getByText('Completed')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Portfolio Manager Decision' })).toBeVisible()
  await shot(page, 'run-decision', undefined, { ...PAGE, height: 1000 })
})

// README, "6. Watch it and read the results": the replay is over in seconds, so the run is shown as
// it was a minute in: the event stream is cut after the bull researcher started, and the analysis
// is reported as running.
test('running analysis', async ({ page, request }) => {
  const id = await replayedRun(request, 'NVDA', '2026-09-29')
  const stream = await (await request.get(`/api/analyses/${id}/events`)).text()
  const blocks = stream.split('\n\n')
  const bullStarted = blocks.findIndex(
    (block) => block.includes('Bull Researcher') && block.includes('in_progress'),
  )
  expect(bullStarted).toBeGreaterThan(0)
  const endBlock = 'event: end\ndata: end'
  await page.route(`**/api/analyses/${id}/events`, (route) =>
    route.fulfill({
      contentType: 'text/event-stream',
      // The `end` event keeps the page from showing "Connection lost, reconnecting...".
      body: [...blocks.slice(0, bullStarted + 1), endBlock].join('\n\n') + '\n\n',
    }),
  )
  await page.route(`**/api/analyses/${id}`, async (route) => {
    const response = await route.fetch()
    const analysis = (await response.json()) as Record<string, unknown>
    await route.fulfill({
      response,
      json: { ...analysis, status: 'RUNNING', rating: null, decision: null, finishedAt: null },
    })
  })
  await page.goto(`/analyses/${id}`)
  await expect(page.locator('.n-tag__content', { hasText: 'Running' })).toBeVisible()
  await expect(page.locator('.agent__status', { hasText: 'Running' })).toBeVisible()
  await shot(page, 'run-live', undefined, { ...PAGE, height: 1100 })
})

// README, "Reports": the list, the contents and the report with its chart.
test('reports page', async ({ page, request }) => {
  test.setTimeout(240_000)
  await replayedRun(request, 'MU', '2026-09-26')
  await replayedRun(request, 'NVDA', '2026-09-27')
  await page.goto('/reports')
  await page.getByRole('button', { name: /NVDA.*2026-09-27/ }).click()
  await expect(page.getByRole('navigation', { name: 'Contents' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Markdown', exact: true })).toBeVisible()
  await shot(page, 'reports')
})
