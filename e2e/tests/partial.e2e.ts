import { expect, test } from '@playwright/test'
import {
  completedAnalysis,
  csrfHeaders,
  DECISION_TEXT,
  loginAsNewUser,
  visibilitySelect,
} from './support'

// Runs as the shared admin, whose session must not change. The two users are created here with
// unique names, because the tests run in parallel and are retried.

test('a partial analysis is a preview for visitors until its owner makes it private', async ({
  request,
  browser,
}) => {
  const ticker = 'TXN'
  const headers = await csrfHeaders(request)
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const roleId = (name: string) => roles.find((role) => role.name === name)!.id

  async function createUser(username: string, roleNames: string[]) {
    const created = await request.post('/api/users', {
      headers,
      data: { username, roleIds: roleNames.map(roleId) },
    })
    expect(created.ok(), await created.text()).toBe(true)
    return (await created.json()) as { user: { id: string }; oneTimePassword: string }
  }

  const suffix = Date.now()
  const nameA = `e2e-partial-owner-${suffix}`
  const nameB = `e2e-partial-reader-${suffix}`
  const a = await createUser(nameA, ['ANALYST', 'PUBLISHER'])
  const b = await createUser(nameB, ['ANALYST'])

  const owner = await loginAsNewUser(browser, nameA, a.oneTimePassword)
  const visitorContext = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const visitor = await visitorContext.newPage()
  try {
    const id = await completedAnalysis(owner.context.request, ticker, undefined, {
      platformModels: true,
    })

    await owner.page.goto(`/analyses/${id}`)
    await visibilitySelect(owner.page).click()
    await owner.page.getByText('Partial (preview for visitors)').click()
    await expect(
      owner.page.getByText('Anyone on the internet will see the preview of this analysis.'),
    ).toBeVisible()
    await owner.page.getByRole('button', { name: 'Confirm' }).click()
    await expect(visibilitySelect(owner.page)).toContainText('Partial')

    await visitor.goto('/')
    await expect(visitor).toHaveURL(/\/$/)
    await expect(visitor.getByPlaceholder('Username')).toHaveCount(0)
    await expect(visitor.getByRole('heading', { name: 'Multi-agent stock analysis' })).toBeVisible()
    const card = visitor.getByRole('link', { name: new RegExp(ticker) })
    await expect(card).toBeVisible()
    expect(await visitor.content()).not.toContain(nameA)

    await card.click()
    await expect(visitor).toHaveURL(new RegExp(`/preview/${id}$`))
    await expect(visitor.getByText(ticker).first()).toBeVisible()
    await expect(visitor.getByText('Market Analysis')).toBeVisible()
    await expect(visitor.getByText('Sentiment report')).toBeVisible()
    await expect(visitor.getByText('Final decision')).toBeVisible()
    const source = await (await visitorContext.request.get(`/api/public/analyses/${id}`)).text()
    expect(source).not.toContain('Retail sentiment')
    expect(source).not.toContain('Export-control')
    expect(source).not.toContain(DECISION_TEXT)
    const html = await visitor.content()
    expect(html).not.toContain('Retail sentiment')
    expect(html).not.toContain(DECISION_TEXT)

    await visitor.locator('.cta').first().getByRole('link', { name: 'Log in' }).click()
    await expect(visitor).toHaveURL(/\/login\?redirect=/)
    await visitor.getByPlaceholder('Username').fill(nameB)
    await visitor.getByPlaceholder('Password').fill(b.oneTimePassword)
    await visitor.getByRole('button', { name: 'Log in' }).click()
    await expect(visitor).toHaveURL(/\/password/)
    const newPassword = 'a-brand-new-password-1'
    await visitor.getByPlaceholder('Current password').fill(b.oneTimePassword)
    await visitor.getByPlaceholder('New password', { exact: true }).fill(newPassword)
    await visitor.getByPlaceholder('Repeat new password').fill(newPassword)
    await visitor.getByRole('button', { name: 'Change password' }).click()
    await expect(visitor).toHaveURL(new RegExp(`/analyses/${id}$`))
    await expect(visitor.getByText(`Shared by ${nameA}`)).toBeVisible()
    await expect(visitor.getByText(DECISION_TEXT).first()).toBeVisible()
    await expect(visitor.getByRole('button', { name: 'Stop' })).toHaveCount(0)
    await expect(visitor.getByRole('button', { name: 'Run again' })).toHaveCount(0)
    await expect(visibilitySelect(visitor)).toHaveCount(0)

    await visibilitySelect(owner.page).click()
    await owner.page.getByText('Private', { exact: true }).click()
    await expect(visibilitySelect(owner.page)).toContainText('Private')

    const anonymous = await browser.newContext({ storageState: { cookies: [], origins: [] } })
    try {
      const page = await anonymous.newPage()
      await page.goto(`/preview/${id}`)
      await expect(page.getByText('Analysis not found.')).toBeVisible()
      await page.goto('/')
      await expect(page.getByRole('link', { name: new RegExp(ticker) })).toHaveCount(0)
    } finally {
      await anonymous.close()
    }
  } finally {
    await owner.context.close()
    await visitorContext.close()
  }
})
