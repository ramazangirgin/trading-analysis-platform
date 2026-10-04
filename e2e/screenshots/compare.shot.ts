import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'

// README, "6. Past analyses": the same ticker run with two different deep-thinking models.
test('compare view', async ({ page, request }) => {
  const mini = await replayedRun(request, 'NVDA', '2026-09-17')
  const full = await replayedRun(request, 'NVDA', '2026-09-18', 'gpt-5.4')

  await page.goto(`/analyses/compare?ids=${mini},${full}`)
  await expect(page.locator('thead th')).toHaveCount(3)
  await expect(page.locator('[data-row="compare.rating"]').getByText('Overweight')).toHaveCount(2)
  await shot(page, 'compare')
})
