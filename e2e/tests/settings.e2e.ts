import { expect, test } from '@playwright/test'
import { csrfHeaders } from './support'

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

test('the admin saves the analysis defaults on the card and finds them after a reload', async ({
  page,
  request,
}) => {
  const read = async () =>
    (await (await request.get('/api/settings/analysis-defaults')).json()) as Record<string, unknown>
  const original = await read()
  try {
    await page.goto('/settings')
    const card = page
      .locator('.n-space', { has: page.getByRole('heading', { name: 'Analysis defaults' }) })
      .last()
    await expect(card.getByRole('button', { name: 'Save' })).toBeEnabled({ timeout: 30_000 })
    // Naive UI's select carries no combobox or option roles.
    await card.locator('.n-base-selection').nth(1).click()
    const option = page
      .locator('.n-base-select-option:not(.n-base-select-option--selected)')
      .first()
    const chosen = ((await option.innerText()) as string).split(' — ')[0]
    await option.click()
    const rounds = card.locator('.n-input-number input')
    await rounds.nth(0).fill('4')
    await rounds.nth(1).fill('5')
    await card.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText('Analysis defaults saved.')).toBeVisible()

    await page.reload()
    await expect(rounds.nth(0)).toHaveValue('4')
    await expect(rounds.nth(1)).toHaveValue('5')
    await expect(card.locator('.n-base-selection').nth(1)).toContainText(chosen)
  } finally {
    await request.put('/api/settings/analysis-defaults', {
      headers: await csrfHeaders(request),
      data: { ...original, version: (await read()).version },
    })
  }
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
