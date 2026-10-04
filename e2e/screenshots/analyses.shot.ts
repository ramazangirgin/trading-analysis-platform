import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'

// README, "6. Past analyses": the Analyses list with two runs ticked for comparing.
test('analyses list with two runs ticked', async ({ page, request }) => {
  // Four replayed runs, one after the other.
  test.setTimeout(240_000)
  await replayedRun(request, 'AMD', '2026-09-22')
  await replayedRun(request, 'MU', '2026-09-23', 'gpt-5.4')
  await replayedRun(request, 'NVDA', '2026-09-24', 'gpt-5.4')
  await replayedRun(request, 'NVDA', '2026-09-25')

  await page.goto('/analyses')
  for (const row of [0, 1]) {
    await page.getByRole('row', { name: /NVDA/ }).nth(row).getByRole('checkbox').click()
  }
  await expect(page.getByRole('button', { name: 'Compare (2)' })).toBeEnabled()
  await shot(page, 'analyses')
})
