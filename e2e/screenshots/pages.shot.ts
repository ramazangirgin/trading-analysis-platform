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

// README, "4. Add an API key": Settings with the provider keys and the Analysis defaults card.
test('settings', async ({ page, request }) => {
  await setProviderKey(request)
  await page.goto('/settings')
  await expect(page.getByRole('row', { name: /OPENAI_API_KEY/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Save' }).last()).toBeEnabled({ timeout: 30_000 })
  const heading = page.getByRole('heading', { name: 'Analysis defaults' })
  await expect(heading).toBeVisible()
  // The card sits below the long list of keys: take the page from the last keys on.
  const box = (await heading.boundingBox())!
  const y = Math.max(0, box.y + (await page.evaluate(() => window.scrollY)) - 250)
  await shot(page, 'settings', undefined, { x: 0, y, width: 1440, height: 900 })
})

// README, "4. Start an analysis": the form, once the catalog has filled in the providers and models.
test('new analysis form', async ({ page, request }) => {
  await setProviderKey(request)
  await page.goto('/analyses/new')
  await page.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('NVDA')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText(/No LLM provider has an API key/)).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Reset to defaults' })).toBeVisible()
  await shot(page, 'new-analysis')
})

// README, "5. Start an analysis": the form of a user without analysis:override-models.
test('new analysis form without override permission', async ({ browser, request }) => {
  await setProviderKey(request)
  const username = 'maria-readonly'
  const password = 'a-brand-new-password-1'
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [roles.find((role) => role.name === 'ANALYST')!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const page = await context.newPage()
  await page.goto('/login')
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(oneTimePassword)
  await page.getByRole('button', { name: 'Log in' }).click()
  await page.getByPlaceholder('Current password').fill(oneTimePassword)
  await page.getByPlaceholder('New password', { exact: true }).fill(password)
  await page.getByPlaceholder('Repeat new password').fill(password)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page).toHaveURL('/')

  await page.goto('/analyses/new')
  await page.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('NVDA')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('Set by the administrator').first()).toBeVisible()
  await shot(page, 'new-analysis-readonly')
  await context.close()
})

// README, "6. Past analyses": the Dashboard with the latest decisions.
test('dashboard', async ({ page, request }) => {
  await setProviderKey(request)
  await replayedRun(request, 'NVDA', '2026-09-28')
  await page.goto('/')
  await expect(page.getByText('Overweight').first()).toBeVisible()
  await shot(page, 'dashboard')
})
