import { expect, test } from '@playwright/test'
import { completedAnalysis, csrfHeaders, DECISION_TEXT, loginAsNewUser } from './support'

// Runs as the shared admin, whose session must not change. The two users are created here with
// unique names, because the tests run in parallel and are retried.

test('a published analysis is read-only for another user until its owner makes it private', async ({
  request,
  browser,
}) => {
  const ticker = 'AVGO'
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
  const nameA = `e2e-publisher-${suffix}`
  const nameB = `e2e-reader-${suffix}`
  const a = await createUser(nameA, ['ANALYST', 'PUBLISHER'])
  const b = await createUser(nameB, ['ANALYST'])

  const publisher = await loginAsNewUser(browser, nameA, a.oneTimePassword)
  const reader = await loginAsNewUser(browser, nameB, b.oneTimePassword)
  try {
    const id = await completedAnalysis(publisher.context.request, ticker, undefined, {
      platformModels: true,
    })

    await publisher.page.goto(`/analyses/${id}`)
    await publisher.page.getByRole('switch').click()
    await publisher.page.getByRole('button', { name: 'Confirm' }).click()
    await expect(publisher.page.getByRole('switch')).toBeChecked()

    await reader.page.goto('/analyses')
    await reader.page.locator('.n-tabs-tab', { hasText: 'Shared with everyone' }).click()
    await expect(reader.page).toHaveURL(/scope=SHARED/)
    const row = reader.page.getByRole('row', { name: new RegExp(ticker) })
    await expect(row.getByRole('cell', { name: nameA })).toBeVisible()
    await expect(row.getByText('Public')).toBeVisible()
    await row.getByText(ticker).click()

    await expect(reader.page).toHaveURL(new RegExp(`/analyses/${id}`))
    await expect(reader.page.getByText(`Shared by ${nameA}`)).toBeVisible()
    await expect(reader.page.getByText(DECISION_TEXT).first()).toBeVisible()
    await expect(reader.page.getByRole('button', { name: 'Stop' })).toHaveCount(0)
    await expect(reader.page.getByRole('button', { name: 'Run again' })).toHaveCount(0)
    await expect(reader.page.locator('.n-tabs-tab', { hasText: 'Reports' })).toBeVisible()
    await expect(reader.page.locator('.n-tabs-tab', { hasText: 'Log' })).toHaveCount(0)
    await expect(reader.page.getByRole('switch')).toHaveCount(0)
    await expect(reader.page.getByRole('button', { name: 'Make private' })).toHaveCount(0)

    await publisher.page.getByRole('switch').click()
    await expect(publisher.page.getByRole('switch')).not.toBeChecked()

    await reader.page.reload()
    await expect(reader.page.getByText('Analysis not found.')).toBeVisible()
    await reader.page.goto('/analyses?scope=SHARED')
    await expect(reader.page.getByRole('row', { name: new RegExp(ticker) })).toHaveCount(0)
  } finally {
    await publisher.context.close()
    await reader.context.close()
  }
})
