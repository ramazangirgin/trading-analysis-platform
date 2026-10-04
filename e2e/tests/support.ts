import { expect, type APIRequestContext } from '@playwright/test'

/** The trade date of the replayed recording (artifact/ta-runner/tests/fixtures/replay-run). */
export const TRADE_DATE = '2026-09-25'

/** A line of the recording's final decision, to find it on a page or in an export. */
export const DECISION_TEXT = 'take a 4% position now'

/** Starts an analysis over the API and waits until ta-runner has replayed it to the end. */
export async function completedAnalysis(request: APIRequestContext, ticker: string) {
  const started = await request.post('/api/analyses', {
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
