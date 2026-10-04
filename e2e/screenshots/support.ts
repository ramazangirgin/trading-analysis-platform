import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Locator, Page } from '@playwright/test'

/** Where the README's screenshots live; SCREENSHOT_DIR writes them elsewhere (to try a shot out). */
const DIR =
  process.env.SCREENSHOT_DIR ??
  join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'screenshots')

/**
 * Saves the page (or one element of it) as docs/screenshots/<name>.png, once the page has settled:
 * no requests in flight, fonts loaded, no animation running.
 */
export async function shot(page: Page, name: string, target?: Locator) {
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  mkdirSync(DIR, { recursive: true })
  const path = join(DIR, `${name}.png`)
  const options = { path, animations: 'disabled' as const }
  await (target ? target.screenshot(options) : page.screenshot(options))
  return path
}
