import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'

// README, "6. Watch it and read the results": a completed run's owner about to make it partial.
test('run detail with the share confirmation', async ({ page, request }) => {
  const id = await replayedRun(request, 'NVDA', '2026-09-25')

  await page.goto(`/analyses/${id}`)
  await page.locator('.n-select').click()
  await page.getByText('Partial (preview for visitors)').click()
  await expect(
    page.getByText('Anyone on the internet will see the preview of this analysis.'),
  ).toBeVisible()
  await shot(page, 'run-share')
})
