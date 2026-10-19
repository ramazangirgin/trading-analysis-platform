import { expect, test } from '@playwright/test'
import { completedAnalysis, csrfHeaders } from './support'

// Runs as the shared admin, whose session must not change. The analyst is created here with a unique
// name, because the tests run in parallel and are retried.

test("an analyst does not see the admin's run, and the admin sees it with its owner", async ({
  page,
  request,
  browser,
}) => {
  const ticker = 'ORCL'
  const id = await completedAnalysis(request, ticker)

  const username = `e2e-analyst-${Date.now()}`
  const newPassword = 'a-brand-new-password-1'
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const analyst = roles.find((role) => role.name === 'ANALYST')
  expect(analyst).toBeDefined()
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [analyst!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const analystPage = await context.newPage()
  try {
    await analystPage.goto('/login')
    await analystPage.getByPlaceholder('Username').fill(username)
    await analystPage.getByPlaceholder('Password').fill(oneTimePassword)
    await analystPage.getByRole('button', { name: 'Log in' }).click()
    await expect(analystPage).toHaveURL(/\/password\?redirect=\/$/)
    await analystPage.getByPlaceholder('Current password').fill(oneTimePassword)
    await analystPage.getByPlaceholder('New password', { exact: true }).fill(newPassword)
    await analystPage.getByPlaceholder('Repeat new password').fill(newPassword)
    await analystPage.getByRole('button', { name: 'Change password' }).click()
    await expect(analystPage).toHaveURL('/')

    await analystPage.goto('/analyses')
    await expect(analystPage.locator('.n-card-header__main', { hasText: 'Analyses' })).toBeVisible()
    await expect(analystPage.getByRole('switch')).toHaveCount(0)
    await expect(analystPage.getByRole('row', { name: new RegExp(ticker) })).toHaveCount(0)

    await analystPage.goto(`/analyses/${id}`)
    await expect(analystPage.getByText('Analysis not found.')).toBeVisible()
  } finally {
    await context.close()
  }

  await page.goto('/analyses')
  await page.getByRole('switch').click()
  await expect(page).toHaveURL(/allUsers=true/)
  await expect(page.getByRole('columnheader', { name: 'Owner' })).toBeVisible()
  await expect(
    page
      .getByRole('row', { name: new RegExp(ticker) })
      .first()
      .getByRole('cell', { name: 'admin' }),
  ).toBeVisible()
})
