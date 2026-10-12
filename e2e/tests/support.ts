import { expect, type APIRequestContext } from '@playwright/test'

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

/** The trade date of the replayed recording (artifact/ta-runner/tests/fixtures/replay-run). */
export const TRADE_DATE = '2026-09-25'

/** A line of the recording's final decision, to find it on a page or in an export. */
export const DECISION_TEXT = 'take a 4% position now'

/**
 * Starts an analysis over the API and waits until ta-runner has replayed it to the end. The tests
 * run in parallel against one platform: every test uses a ticker of its own, so a new test that
 * starts an analysis picks one no other test uses.
 */
export async function completedAnalysis(request: APIRequestContext, ticker: string) {
  const started = await request.post('/api/analyses', {
    headers: await csrfHeaders(request),
    data: {
      ticker,
      tradeDate: TRADE_DATE,
      analysts: ['MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS'],
      llmProvider: 'openai',
      deepThinkLlm: 'gpt-5.4-mini',
      quickThinkLlm: 'gpt-5.4-mini',
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
