import { expect, test } from '@playwright/test'
import { csrfHeaders } from '../tests/support'
import { replayedRun, versionOf } from './runs'
import { shot } from './support'

// README, "7. Past analyses": the Analyses list on the All users tab, with the Owner column, one
// public run and two runs ticked for comparing.
test('analyses list with two runs ticked', async ({ page, request }) => {
  // Four replayed runs, one after the other.
  test.setTimeout(240_000)
  await replayedRun(request, 'AMD', '2026-09-22')
  await replayedRun(request, 'MU', '2026-09-23', 'gpt-5.4')
  await replayedRun(request, 'NVDA', '2026-09-24', 'gpt-5.4')
  const shared = await replayedRun(request, 'NVDA', '2026-09-25')
  const published = await request.put(`/api/analyses/${shared}/visibility`, {
    headers: await csrfHeaders(request),
    data: { visibility: 'PUBLIC', version: await versionOf(request, shared) },
  })
  expect(published.ok(), await published.text()).toBe(true)

  await page.goto('/analyses?scope=ALL')
  await expect(page.getByRole('columnheader', { name: 'Owner' })).toBeVisible()
  for (const row of [0, 1]) {
    await page.getByRole('row', { name: /NVDA/ }).nth(row).getByRole('checkbox').click()
  }
  await expect(page.getByRole('button', { name: 'Compare (2)' })).toBeEnabled()
  await shot(page, 'analyses')
})
