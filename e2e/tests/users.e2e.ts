import { expect, test, type Page } from '@playwright/test'
import { E2E_USER } from './support'

// Runs as the shared admin, whose password and session must not change. The users created here are
// the ones that log in, change their password and get disabled; the tests run in parallel and are
// retried, so each run creates a user with a unique name.

async function logIn(page: Page, username: string, password: string) {
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

test('an admin creates a user who logs in with the one-time password, then disables them', async ({
  page,
  browser,
}) => {
  const username = `e2e-user-${Date.now()}`
  const newPassword = 'a-brand-new-password-1'

  await page.goto('/')
  await page.getByRole('button', { name: 'Admin', exact: true }).click()
  await page.getByText('Users', { exact: true }).click()
  await expect(page).toHaveURL('/admin/users')

  await page.getByRole('button', { name: 'New user' }).click()
  const dialog = page.getByRole('dialog', { name: 'New user' })
  await dialog.getByRole('textbox').first().fill(username)
  await dialog.locator('.n-base-selection').click()
  await page.locator('.n-base-select-option', { hasText: 'VIEWER' }).click()
  await dialog.getByText('New user', { exact: true }).click()
  await dialog.getByRole('button', { name: 'Create' }).click()

  await expect(page.getByText('This password is shown only this once.')).toBeVisible()
  const oneTimePassword = await page.locator('input[readonly]').inputValue()
  expect(oneTimePassword).not.toBe('')
  await page.getByRole('button', { name: 'Done' }).click()

  const row = page.getByRole('row', { name: new RegExp(username) })
  await expect(row).toContainText('VIEWER')
  await expect(row).toContainText('One-time password')

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const userPage = await context.newPage()
  try {
    await userPage.goto('/login')
    await logIn(userPage, username, oneTimePassword)
    await expect(userPage).toHaveURL('/password')
    await userPage.getByPlaceholder('Current password').fill(oneTimePassword)
    await userPage.getByPlaceholder('New password', { exact: true }).fill(newPassword)
    await userPage.getByPlaceholder('Repeat new password').fill(newPassword)
    await userPage.getByRole('button', { name: 'Change password' }).click()
    await expect(userPage).toHaveURL('/')
    await expect(userPage.getByRole('button', { name: username })).toBeVisible()
    await expect(userPage.getByRole('button', { name: 'Admin', exact: true })).toHaveCount(0)

    await row.getByRole('button', { name: 'Disable' }).click()
    await page.getByRole('button', { name: 'Confirm' }).click()
    await expect(row).toContainText('Disabled')

    await userPage.goto('/analyses')
    await expect(userPage).toHaveURL(/\/login/)
    await logIn(userPage, username, newPassword)
    await expect(userPage.getByText('Wrong username or password.')).toBeVisible()
  } finally {
    await context.close()
  }
})

test('an admin cannot disable themselves: the button is disabled and says why', async ({
  page,
}) => {
  await page.goto('/admin/users')
  const row = page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: E2E_USER, exact: true }) })
  const disable = row.getByRole('button', { name: 'Disable' })
  await expect(disable).toBeDisabled()

  await disable.locator('..').hover()
  await expect(page.getByText('You cannot disable your own account.')).toBeVisible()
})
