import { expect, test } from '@playwright/test'
import { completedAnalysis, csrfHeaders, loginAsNewUser } from './support'

// Runs as the shared admin, whose session must not change. The analyst is created here with a unique
// name, because the tests run in parallel and are retried.

test("an analyst does not see the admin's run, and the admin sees it with its owner", async ({
  page,
  request,
  browser,
}) => {
  const ticker = 'MU'
  const id = await completedAnalysis(request, ticker)

  const username = `e2e-analyst-${Date.now()}`
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const analyst = roles.find((role) => role.name === 'ANALYST')
  expect(analyst).toBeDefined()
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [analyst!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const { page: analystPage, context } = await loginAsNewUser(browser, username, oneTimePassword)
  try {
    await analystPage.goto('/analyses')
    await expect(analystPage.locator('.n-card-header__main', { hasText: 'Analyses' })).toBeVisible()
    await expect(
      analystPage.locator('.n-tabs-tab', { hasText: 'Shared with everyone' }),
    ).toBeVisible()
    await expect(analystPage.locator('.n-tabs-tab', { hasText: 'All users' })).toHaveCount(0)
    await expect(analystPage.getByRole('row', { name: new RegExp(ticker) })).toHaveCount(0)

    await analystPage.goto(`/analyses/${id}`)
    await expect(analystPage.getByText('Analysis not found.')).toBeVisible()
  } finally {
    await context.close()
  }

  await page.goto('/analyses')
  await page.locator('.n-tabs-tab', { hasText: 'All users' }).click()
  await expect(page).toHaveURL(/scope=ALL/)
  await expect(page.getByRole('columnheader', { name: 'Owner' })).toBeVisible()
  await expect(
    page
      .getByRole('row', { name: new RegExp(ticker) })
      .first()
      .getByRole('cell', { name: 'admin' }),
  ).toBeVisible()
})
