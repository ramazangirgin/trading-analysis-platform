import { expect, test } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'
import { csrfHeaders } from '../tests/support'

// README, "3. Add an API key": Settings with the provider keys.
test('settings', async ({ page }) => {
  await page.goto('/settings')
  await expect(page.getByRole('row', { name: /OPENAI_API_KEY/ })).toBeVisible()
  await shot(page, 'settings')
})

// README, "4. Start an analysis": the form, once the catalog has filled in the providers and models.
test('new analysis form', async ({ page }) => {
  await page.goto('/analyses/new')
  await page.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('NVDA')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  await shot(page, 'new-analysis')
})

// README, "4. Start an analysis": the presets listed in Settings.
test('presets in settings', async ({ page, request }) => {
  const headers = await csrfHeaders(request)
  const values = {
    llmProvider: 'deepseek',
    deepThinkLlm: 'deepseek-v4-pro',
    quickThinkLlm: 'deepseek-v4-flash',
    outputLanguage: 'English',
  }
  for (const preset of [
    {
      name: 'DeepSeek, all analysts',
      values: {
        ...values,
        analysts: ['market', 'social', 'news', 'fundamentals'],
        maxDebateRounds: 2,
        maxRiskDiscussRounds: 1,
      },
    },
    {
      name: 'Quick market check',
      values: { ...values, analysts: ['market'], maxDebateRounds: 1, maxRiskDiscussRounds: 1 },
    },
  ]) {
    const response = await request.post('/api/presets', { headers, data: preset })
    expect(response.ok(), await response.text()).toBe(true)
  }

  await page.goto('/settings')
  const deletes = page.getByRole('button', { name: 'Delete' })
  await expect(deletes).toHaveCount(2)
  // From the "Presets" heading down to the last row: the section has no element of its own.
  const heading = await page.getByText('Presets', { exact: true }).last().boundingBox()
  const last = await deletes.last().boundingBox()
  expect(heading && last).toBeTruthy()
  const y = heading!.y - 8
  await shot(page, 'presets', undefined, {
    x: 186,
    y,
    width: 1068,
    height: last!.y + last!.height + 20 - y,
  })
})

// README, "6. Past analyses": the Dashboard with the latest decisions.
test('dashboard', async ({ page, request }) => {
  await replayedRun(request, 'NVDA', '2026-09-28')
  await page.goto('/')
  await expect(page.getByText('Overweight').first()).toBeVisible()
  await shot(page, 'dashboard')
})

// README, "7. Reports and export": the report list, contents and the report.
test('reports', async ({ page, request }) => {
  await replayedRun(request, 'NVDA', '2026-09-26')
  await page.goto('/reports')
  await page.getByRole('button', { name: /NVDA.*2026-09-26/ }).click()
  await expect(page.getByRole('navigation', { name: 'Contents' })).toBeVisible()
  await shot(page, 'reports')
})
