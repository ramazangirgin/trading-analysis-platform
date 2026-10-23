import { expect, type APIRequestContext, type Browser } from '@playwright/test'

/** The first admin that start-platform.sh configures (PLATFORM_ADMIN_USERNAME / _PASSWORD). */
export const E2E_USER = 'admin'
export const E2E_PASSWORD = 'e2e-test-password'

/**
 * The CSRF header of an API call that changes something: the token is the XSRF-TOKEN cookie of the
 * request context's session.
 */
export async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const { cookies } = await request.storageState()
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

/**
 * Logs in a user that an admin has just created with a one-time password, changes that password and
 * returns the page with its context (the caller closes the context). The shared admin's session is
 * not touched.
 */
export async function loginAsNewUser(browser: Browser, username: string, oneTimePassword: string) {
  const newPassword = 'a-brand-new-password-1'
  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const page = await context.newPage()
  await page.goto('/login')
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(oneTimePassword)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/password\?redirect=\/$/)
  await page.getByPlaceholder('Current password').fill(oneTimePassword)
  await page.getByPlaceholder('New password', { exact: true }).fill(newPassword)
  await page.getByPlaceholder('Repeat new password').fill(newPassword)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page).toHaveURL('/')
  return { page, context }
}

/** The trade date of the replayed recording (artifact/ta-runner/tests/fixtures/replay-run). */
export const TRADE_DATE = '2026-09-25'

/** A line of the recording's final decision, to find it on a page or in an export. */
export const DECISION_TEXT = 'take a 4% position now'

/**
 * Starts an analysis over the API and waits until ta-runner has replayed it to the end. The tests
 * run in parallel against one platform: every test uses a ticker of its own, so a new test that
 * starts an analysis picks one no other test uses. Without `analysis:override-models` (an analyst),
 * pass `platformModels` to leave the models to the platform's defaults.
 */
export async function completedAnalysis(
  request: APIRequestContext,
  ticker: string,
<<<<<<< HEAD
  { platformModels = false } = {},
) {
  const models = platformModels
    ? {}
    : { llmProvider: 'openai', deepThinkLlm: 'gpt-5.4-mini', quickThinkLlm: 'gpt-5.4-mini' }
=======
  outputLanguage?: string,
) {
>>>>>>> origin/main
  const started = await request.post('/api/analyses', {
    headers: await csrfHeaders(request),
    data: {
      ticker,
      outputLanguage,
      tradeDate: TRADE_DATE,
      analysts: ['MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS'],
      ...models,
    },
  })
  expect(started.ok(), await started.text()).toBe(true)
  const { id } = (await started.json()) as { id: string }
  await expect
    .poll(
      async () => {
        const analysis = await request.get(`/api/analyses/${id}`)
        return ((await analysis.json()) as { status: string }).status
      },
      { timeout: 45_000 },
    )
    .toBe('COMPLETED')
  return id
}
