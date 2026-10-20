import { expect, test } from '@playwright/test'

test('an API key is saved, shown masked and removed', async ({ page }) => {
  const key = 'sk-e2e-0123456789-abcd'
  await page.goto('/settings')
  const row = page.getByRole('row', { name: /OPENAI_API_KEY/ })
  await expect(row.getByText('Not set')).toBeVisible()

  await row.locator('input[type="password"]').fill(key)
  await row.getByRole('button', { name: 'Save' }).click()
  await expect(row.getByText('In the platform')).toBeVisible()
  await expect(row.getByText('••••abcd')).toBeVisible()
  await expect(page.locator('body')).not.toContainText(key)

  await page.reload()
  await expect(row.getByText('••••abcd')).toBeVisible()
  await expect(page.locator('body')).not.toContainText(key)

  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(row.getByText('Not set')).toBeVisible()
})

test('a preset saved on New analysis is listed in settings, loads back and is deleted', async ({
  page,
}) => {
  const name = 'E2E preset'
  await page.goto('/analyses/new')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  const sentiment = page.getByRole('checkbox', { name: 'Sentiment (social media)' })
  await sentiment.uncheck()
  await page.getByRole('button', { name: 'Save as preset' }).click()
  await page.getByPlaceholder('Name').fill(name)
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByText(`"${name}" saved.`)).toBeVisible()

  await page.goto('/settings')
  const row = page.getByRole('row', { name: /Market, News, Fundamentals/ })
  await expect(row.getByRole('textbox')).toHaveValue(name)

  await page.goto('/analyses/new')
  await expect(sentiment).toBeChecked()
  // Naive UI's select carries no combobox or option roles.
  await page.locator('.n-base-selection', { hasText: 'Load a preset' }).click()
  await page.locator('.n-base-select-option', { hasText: name }).click()
  await expect(sentiment).not.toBeChecked()

  await page.goto('/settings')
  await row.getByRole('button', { name: 'Delete' }).click()
  await page.getByRole('button', { name: 'Confirm' }).click()
  await expect(page.getByText(`"${name}" deleted.`)).toBeVisible()
  await expect(page.getByText('No presets yet.', { exact: false })).toBeVisible()
})
