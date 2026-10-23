import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { completedAnalysis, TRADE_DATE } from './support'

const ENGLISH = /not investment advice/
const TURKISH = /yatırım tavsiyesi değildir/

test.describe('logged out', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('the login page shows the disclaimer and links to the public About page', async ({
    page,
  }) => {
    await page.goto('/login')
    await expect(page.getByRole('note')).toContainText(ENGLISH)

    await page.getByRole('link', { name: 'About' }).first().click()
    await expect(page).toHaveURL('/about')
    await expect(page.getByRole('heading', { name: 'About' })).toBeVisible()
    await expect(page.getByRole('note')).toHaveCount(2)
  })
})

test('the dashboard and the About page show the disclaimer', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('note')).toContainText(ENGLISH)

  await page.getByRole('link', { name: 'About' }).click()
  await expect(page).toHaveURL('/about')
  await expect(page.getByRole('heading', { name: 'About' })).toBeVisible()
  await expect(page.getByRole('note')).toHaveCount(2)
})

test('switching the screen language switches the header disclaimer', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('note')).toContainText(ENGLISH)

  await page.getByLabel('Language').click()
  await page.getByText('Türkçe', { exact: true }).click()
  await expect(page.getByRole('note')).toContainText(TURKISH)
})

test('an English report ends with the disclaimer, in the page and in the Markdown export', async ({
  page,
  request,
}) => {
  await completedAnalysis(request, 'ORCL')

  await page.goto('/reports')
  await page.getByRole('button', { name: new RegExp(`ORCL.*${TRADE_DATE}`) }).click()
  await expect(page.getByRole('navigation', { name: 'Contents' })).toBeVisible()
  await expect(page.getByRole('note')).toHaveCount(2)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Markdown', exact: true }).click()
  const markdown = await readFile(await (await download).path(), 'utf8')
  expect(markdown.trim()).toMatch(/not investment advice[^\n]*$/)
})

test('a Turkish report shows the Turkish disclaimer under an English screen', async ({
  page,
  request,
}) => {
  await completedAnalysis(request, 'CSCO', 'Turkish')

  await page.goto('/reports')
  await page.getByRole('button', { name: new RegExp(`CSCO.*${TRADE_DATE}`) }).click()
  await expect(page.getByRole('navigation', { name: 'Contents' })).toBeVisible()
  await expect(page.getByText(TURKISH)).toBeVisible()
  await expect(page.getByText(ENGLISH)).toHaveCount(1)
})

test('the header disclaimer does not make the page scroll sideways on a phone', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 800 })
  await page.goto('/')
  await expect(page.getByRole('note')).toBeVisible()
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
})
