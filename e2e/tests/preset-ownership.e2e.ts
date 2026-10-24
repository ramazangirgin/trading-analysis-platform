import { expect, test } from '@playwright/test'
import { csrfHeaders, loginAsNewUser } from './support'

// Runs as the shared admin, whose session must not change. The analyst and the presets are created
// with unique names, because the tests run in parallel and are retried.

test("an analyst sees only their own presets, and the admin sees everyone's with the owner", async ({
  page,
  request,
  browser,
}) => {
  const stamp = Date.now()
  const username = `e2e-preset-analyst-${stamp}`
  const analystPreset = `Analyst preset ${stamp}`
  const adminPreset = `Admin preset ${stamp}`

  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const analyst = roles.find((role) => role.name === 'ANALYST')
  expect(analyst).toBeDefined()
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [analyst!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const adminCreated = await request.post('/api/presets', {
    headers: await csrfHeaders(request),
    data: { name: adminPreset, values: { llmProvider: 'deepseek', analysts: ['MARKET'] } },
  })
  expect(adminCreated.ok(), await adminCreated.text()).toBe(true)
  const { id: adminPresetId } = (await adminCreated.json()) as { id: string }

  try {
    const { page: analystPage, context } = await loginAsNewUser(browser, username, oneTimePassword)
    try {
      await analystPage.goto('/analyses/new')
      await expect(analystPage.getByText(/This provider needs its key/)).toBeVisible({
        timeout: 30_000,
      })
      await analystPage.getByRole('checkbox', { name: 'Sentiment (social media)' }).uncheck()
      await analystPage.getByRole('button', { name: 'Save as preset' }).click()
      await analystPage.getByPlaceholder('Name').fill(analystPreset)
      await analystPage.getByRole('button', { name: 'Save', exact: true }).click()
      await expect(analystPage.getByText(`"${analystPreset}" saved.`)).toBeVisible()

      await analystPage.goto('/settings')
      const row = analystPage.getByRole('row', { name: /Market, News, Fundamentals/ })
      await expect(row.getByRole('textbox')).toHaveValue(analystPreset)
      await expect(
        analystPage.getByRole('row').filter({ has: analystPage.getByRole('textbox') }),
      ).toHaveCount(1)
      await expect(analystPage.getByRole('switch')).toHaveCount(0)
      await expect(analystPage.getByRole('columnheader', { name: 'Owner' })).toHaveCount(0)

      await analystPage.goto('/analyses/new')
      // Naive UI's select carries no combobox or option roles.
      await analystPage.locator('.n-base-selection', { hasText: 'Load a preset' }).click()
      await expect(
        analystPage.locator('.n-base-select-option', { hasText: analystPreset }),
      ).toBeVisible()
      await expect(
        analystPage.locator('.n-base-select-option', { hasText: adminPreset }),
      ).toHaveCount(0)
    } finally {
      await context.close()
    }

    await page.goto('/settings')
    await expect(page.getByRole('columnheader', { name: 'Owner' })).toHaveCount(0)
    await page.getByRole('switch').click()
    await expect(page.getByRole('columnheader', { name: 'Owner' })).toBeVisible()
    const analystRow = page.getByRole('row', { name: new RegExp(username) })
    await expect(analystRow.getByRole('textbox')).toHaveValue(analystPreset)
    const adminRow = page.getByRole('row', { name: /deepseek/ }).filter({ hasText: 'admin' })
    await expect(adminRow.getByRole('textbox')).toHaveValue(adminPreset)
    await expect(adminRow.getByRole('cell', { name: 'admin', exact: true })).toBeVisible()

    await analystRow.getByRole('button', { name: 'Delete' }).click()
    await page.getByRole('button', { name: 'Confirm' }).click()
    await expect(page.getByText(`"${analystPreset}" deleted.`)).toBeVisible()
  } finally {
    await request.delete(`/api/presets/${adminPresetId}`, { headers: await csrfHeaders(request) })
  }
})
