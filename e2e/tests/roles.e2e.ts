import { expect, test, type Page } from '@playwright/test'
import { csrfHeaders } from './support'

// Runs as the shared admin. The tests run in parallel and are retried, so each run creates a role
// and a user with a unique name.

async function logIn(page: Page, username: string, password: string) {
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

test('a custom role gives exactly its permissions, and a change applies without logging in again', async ({
  page,
  request,
  browser,
}) => {
  const suffix = Date.now()
  const roleName = `e2e-role-${suffix}`
  const username = `e2e-role-user-${suffix}`
  const newPassword = 'a-brand-new-password-1'

  await page.goto('/')
  await page.getByRole('button', { name: 'Admin', exact: true }).click()
  await page.getByText('Roles', { exact: true }).click()
  await expect(page).toHaveURL('/admin/roles')

  await page.getByRole('button', { name: 'New role' }).click()
  const dialog = page.getByRole('dialog', { name: 'New role' })
  await dialog.getByRole('textbox').first().fill(roleName)
  await dialog.getByText('analysis:read', { exact: true }).click()
  await dialog.getByRole('button', { name: 'Create' }).click()

  const row = page.getByRole('row', { name: new RegExp(roleName) })
  await expect(row).toContainText('analysis:read')
  await expect(row).not.toContainText('analysis:run')

  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const roleId = roles.find((role) => role.name === roleName)!.id
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [roleId] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const oneTimePassword = ((await created.json()) as { oneTimePassword: string }).oneTimePassword

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const userPage = await context.newPage()
  try {
    await userPage.goto('/login')
    await logIn(userPage, username, oneTimePassword)
    await expect(userPage).toHaveURL(/\/password\?redirect=\/$/)
    await userPage.getByPlaceholder('Current password').fill(oneTimePassword)
    await userPage.getByPlaceholder('New password', { exact: true }).fill(newPassword)
    await userPage.getByPlaceholder('Repeat new password').fill(newPassword)
    await userPage.getByRole('button', { name: 'Change password' }).click()
    await expect(userPage).toHaveURL('/')
    const nav = userPage.getByRole('navigation')
    await expect(nav.getByText('Analyses', { exact: true })).toBeVisible()
    await expect(nav.getByText('New analysis', { exact: true })).toHaveCount(0)
    await expect(nav.getByText('Settings', { exact: true })).toHaveCount(0)

    await page.reload()
    await expect(row.getByRole('button', { name: 'Delete' })).toBeDisabled()
    await row.getByRole('button', { name: 'Edit' }).click()
    const edit = page.getByRole('dialog', { name: new RegExp(roleName) })
    await edit.getByText('analysis:run', { exact: true }).click()
    await edit.getByRole('button', { name: 'Save' }).click()
    await expect(row).toContainText('analysis:run')

    await userPage.reload()
    await expect(userPage.getByText('New analysis', { exact: true })).toBeVisible()
    await expect(nav.getByText('Settings', { exact: true })).toHaveCount(0)
  } finally {
    await context.close()
  }
})
