import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { completedAnalysis, DECISION_TEXT, TRADE_DATE } from './support'

test('a finished analysis is in the reports and exports as Markdown', async ({ page, request }) => {
  await completedAnalysis(request, 'AMD')

  await page.goto('/reports')
  await page.getByRole('button', { name: new RegExp(`AMD.*${TRADE_DATE}`) }).click()
  await expect(page.getByRole('navigation', { name: 'Contents' })).toBeVisible()
  await expect(page.getByText(DECISION_TEXT).first()).toBeVisible()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Markdown', exact: true }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/AMD.*\.md$/)
  const markdown = await readFile(await file.path(), 'utf8')
  expect(markdown).toContain('Overweight')
  expect(markdown).toContain(DECISION_TEXT)
})
