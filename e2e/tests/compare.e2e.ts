import { expect, test } from '@playwright/test'
import { completedAnalysis, DECISION_TEXT, TRADE_DATE } from './support'

test('two runs are compared side by side', async ({ page, request }) => {
  await completedAnalysis(request, 'INTC')
  await completedAnalysis(request, 'QCOM')

  await page.goto('/analyses')
  for (const ticker of ['INTC', 'QCOM']) {
    // A CI retry runs against the same jar, so earlier attempts may have left rows of the same ticker.
    await page
      .getByRole('row', { name: new RegExp(`${ticker}.*${TRADE_DATE}`) })
      .first()
      .getByRole('checkbox')
      .click()
  }
  // Ticking a checkbox does not open the run.
  await expect(page).toHaveURL(/\/analyses$/)
  await page.getByRole('button', { name: 'Compare (2)' }).click()

  await expect(page).toHaveURL(/\/analyses\/compare\?ids=[^,]+,[^,]+$/)
  await expect(page.getByRole('columnheader', { name: /INTC/ })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: /QCOM/ })).toBeVisible()
  // One label column and one column per run.
  await expect(page.locator('thead th')).toHaveCount(3)

  const decision = page.locator('[data-row="compare.rating"]')
  await expect(decision.getByText('Overweight')).toHaveCount(2)
  await expect(page.locator('[data-row="compare.decision"]').getByText(DECISION_TEXT)).toHaveCount(
    2,
  )
  await expect(page.locator('.n-collapse-item__header', { hasText: 'Market report' })).toBeVisible()
})
