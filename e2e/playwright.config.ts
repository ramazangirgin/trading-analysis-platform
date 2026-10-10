import { defineConfig, devices } from '@playwright/test'

// End-to-end tests of the whole platform: the main flows in a real browser against the built jar,
// which start-platform.sh starts with a throwaway home and ta-runner replaying a recording.
// `mise run e2e` builds what they need and runs them.
const port = Number(process.env.E2E_PORT ?? 8090)

export default defineConfig({
  testDir: 'tests',
  testMatch: '**/*.e2e.ts',
  globalSetup: './tests/global-setup.ts',
  // One platform for every worker: the tests share it and stay independent (an analysis ticker of
  // their own, no global state they assume they own), so they run side by side. GitHub's runners
  // have 4 vCPUs: three Chrome workers, the jar, PostgreSQL and ta-runner fit. E2E_WORKERS=1 runs
  // them one at a time, for debugging.
  workers: Number(process.env.E2E_WORKERS ?? 3),
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  // Pages wait on the backend and, through it, on ta-runner; a busy machine needs more than 5s.
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    locale: 'en-US',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chrome',
      use: {
        ...devices['Desktop Chrome'],
        // The installed Google Chrome (GitHub's runners have it): Playwright's own Chromium does not
        // run everywhere (e.g. macOS 12). E2E_BROWSER_CHANNEL picks another channel, such as msedge.
        channel: process.env.E2E_BROWSER_CHANNEL || 'chrome',
      },
    },
  ],
  webServer: {
    command: 'sh start-platform.sh',
    url: `http://127.0.0.1:${port}/actuator/health`,
    env: { E2E_PORT: String(port) },
    timeout: 120_000,
    stdout: 'pipe',
    reuseExistingServer: false,
  },
})
