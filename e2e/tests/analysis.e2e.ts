import { expect, test } from '@playwright/test'
import { DECISION_TEXT } from './support'

test('a new analysis runs live and ends with its decision', async ({ page }) => {
  await page.goto('/analyses/new')
  await page.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('NVDA')
  // The provider and models come from ta-runner's catalog; the key hint shows once they are in.
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: 'Start analysis' }).click()

  await expect(page).toHaveURL(/\/analyses\/[^/]+$/)
  await expect(page.getByText('NVDA', { exact: true })).toBeVisible()
  // Live: the pipeline and the feed fill in while the recording plays (about nine seconds).
  await expect(page.getByText('Market Analyst').first()).toBeVisible()
  await expect(page.getByText('Running').first()).toBeVisible()

  await expect(page.getByText(DECISION_TEXT).first()).toBeVisible({ timeout: 45_000 })
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()
  await expect(page.getByText('Overweight', { exact: true }).first()).toBeVisible()

  // Naive UI's tabs carry no tab role.
  await page.locator('.n-tabs-tab', { hasText: 'Reports' }).click()
  const sections = page.locator('.n-collapse-item__header')
  await expect(sections.filter({ hasText: 'Market report' })).toBeVisible()
  await expect(sections.filter({ hasText: 'Trader plan' })).toBeVisible()
})
