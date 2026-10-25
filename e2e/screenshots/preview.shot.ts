import { expect, test } from '@playwright/test'
import { csrfHeaders } from '../tests/support'
import { replayedRun, versionOf } from './runs'
import { shot } from './support'

// README, "3. Log in": the preview page of a partial analysis, as a visitor sees it.
test('preview page for a visitor', async ({ browser, request }) => {
  const id = await replayedRun(request, 'MU', '2026-09-21')
  const changed = await request.put(`/api/analyses/${id}/visibility`, {
    headers: await csrfHeaders(request),
    data: { visibility: 'PARTIAL', version: await versionOf(request, id) },
  })
  expect(changed.ok(), await changed.text()).toBe(true)

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  try {
    const page = await context.newPage()
    await page.goto(`/preview/${id}`)
    await expect(page.getByText('Market Analysis')).toBeVisible()
    await expect(page.getByText('Sentiment report')).toBeVisible()
    await shot(page, 'preview')
  } finally {
    await context.close()
  }
})
