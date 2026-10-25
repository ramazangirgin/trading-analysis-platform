import { expect, test } from '@playwright/test'
import { csrfHeaders } from '../tests/support'
import { replayedRun, versionOf } from './runs'
import { shot } from './support'

// README, "3. Log in": the landing page a visitor sees, with two partial analyses.
test('landing page for a visitor', async ({ browser, request }) => {
  test.setTimeout(120_000)
  for (const [ticker, date] of [
    ['AMD', '2026-09-20'],
    ['MU', '2026-09-21'],
  ]) {
    const id = await replayedRun(request, ticker, date)
    const changed = await request.put(`/api/analyses/${id}/visibility`, {
      headers: await csrfHeaders(request),
      data: { visibility: 'PARTIAL', version: await versionOf(request, id) },
    })
    expect(changed.ok(), await changed.text()).toBe(true)
  }

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  try {
    const page = await context.newPage()
    await page.goto('/')
    await expect(page.getByRole('link', { name: /AMD/ }).first()).toBeVisible()
    await expect(page.getByRole('link', { name: /MU/ }).first()).toBeVisible()
    await shot(page, 'landing')
  } finally {
    await context.close()
  }
})
