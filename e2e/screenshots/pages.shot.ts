import { expect, test, type APIRequestContext } from '@playwright/test'
import { replayedRun } from './runs'
import { shot } from './support'
import { csrfHeaders } from '../tests/support'

/** A dummy provider key, so the pages show no "no LLM provider has an API key" warning. */
async function setProviderKey(request: APIRequestContext) {
  const response = await request.put('/api/secrets/OPENAI_API_KEY', {
    headers: await csrfHeaders(request),
    data: { value: 'sk-screenshot-demo-0000' },
  })
  expect(response.ok(), await response.text()).toBe(true)
}

// README, "3. Add an API key": Settings with the provider keys.
test('settings', async ({ page, request }) => {
  await setProviderKey(request)
  await page.goto('/settings')
  await expect(page.getByRole('row', { name: /OPENAI_API_KEY/ })).toBeVisible()
  await shot(page, 'settings')
})

// README, "4. Start an analysis": the form, once the catalog has filled in the providers and models.
test('new analysis form', async ({ page, request }) => {
  await setProviderKey(request)
  await page.goto('/analyses/new')
  await page.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('NVDA')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText(/No LLM provider has an API key/)).toHaveCount(0)
  await shot(page, 'new-analysis')
})

// README, "6. Past analyses": the Dashboard with the latest decisions.
test('dashboard', async ({ page, request }) => {
  await setProviderKey(request)
  await replayedRun(request, 'NVDA', '2026-09-28')
  await page.goto('/')
  await expect(page.getByText('Overweight').first()).toBeVisible()
  await shot(page, 'dashboard')
})
