import { request, type FullConfig } from '@playwright/test'

/**
 * Warms the backend's catalog and engine version cache before the tests: the first request starts
 * ta-runner, which imports TradingAgents and takes seconds (more on a busy CI machine).
 */
export default async function globalSetup(config: FullConfig) {
  const api = await request.newContext({ baseURL: config.projects[0]!.use.baseURL })
  try {
    for (const path of ['/api/catalog', '/api/health']) {
      const response = await api.get(path, { timeout: 120_000 })
      if (!response.ok()) throw new Error(`${path}: ${response.status()} ${await response.text()}`)
    }
  } finally {
    await api.dispose()
  }
}
