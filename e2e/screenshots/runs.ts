import { expect, type APIRequestContext } from '@playwright/test'

/**
 * Starts an analysis over the API and waits until ta-runner has replayed it. The recording's reports
 * are the same for every run; the ticker, date and models make the runs differ. The platform runs one
 * analysis per ticker and date at a time, so runs of one ticker need different dates.
 */
export async function replayedRun(
  request: APIRequestContext,
  ticker: string,
  tradeDate: string,
  deepThinkLlm = 'gpt-5.4-mini',
  quickThinkLlm = 'gpt-5.4-mini',
) {
  const started = await request.post('/api/analyses', {
    data: {
      ticker,
      tradeDate,
      analysts: ['MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS'],
      llmProvider: 'openai',
      deepThinkLlm,
      quickThinkLlm,
    },
  })
  expect(started.ok(), await started.text()).toBe(true)
  const { id } = (await started.json()) as { id: string }
  await expect
    .poll(
      async () =>
        ((await (await request.get(`/api/analyses/${id}`)).json()) as { status: string }).status,
      { timeout: 45_000 },
    )
    .toBe('COMPLETED')
  return id
}
