import { defineConfig, type PlaywrightTestConfig } from '@playwright/test'
import base from './playwright.config'

// The README's screenshots (docs/screenshots/), taken the way the end-to-end tests run: the built
// jar, started by start-platform.sh with a throwaway home and ta-runner replaying a recording, in
// the installed Google Chrome. Each screenshots/*.shot.ts sets up what a page needs (over the API)
// and saves it with shot(). `mise run screenshots` builds what they need and takes them all;
// `mise run screenshots compare` only those whose file or title matches.
const server = base.webServer as Exclude<PlaywrightTestConfig['webServer'], unknown[] | undefined>

export default defineConfig({
  ...base,
  testDir: 'screenshots',
  testMatch: '**/*.shot.ts',
  // Login stays off, as in the app's default: the README's pages show no user menu. Only the warm-up
  // runs before the shots, and no session is stored.
  globalSetup: './tests/warm-up.ts',
  use: { ...base.use, storageState: undefined },
  webServer: { ...server, env: { ...server?.env, PLATFORM_AUTH_ENABLED: 'false' } },
  retries: 0,
  forbidOnly: false,
  reporter: 'list',
  // One at a time: list pages (analyses) must show the same rows on every run.
  workers: 1,
  fullyParallel: false,
  // On the project, not on `use`: the project's device (Desktop Chrome) would override the size.
  projects: (base.projects ?? []).map((project) => ({
    ...project,
    use: {
      ...project.use,
      // The size and theme of the existing screenshots.
      viewport: { width: 1440, height: 900 },
      deviceScaleFactor: 1,
      colorScheme: 'dark' as const,
      screenshot: 'off' as const,
      trace: 'off' as const,
    },
  })),
})
