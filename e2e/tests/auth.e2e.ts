import { expect, test, type Page } from '@playwright/test'
import { E2E_PASSWORD, E2E_USER } from './support'

// A fresh context, logged out, and the login through the form: the shared session of the other
// tests must stay alive, and logging out here would end it.
test.use({ storageState: { cookies: [], origins: [] } })

// Failed logins here must stay below the account lockout (5 in a row) and the per-address limit
// (20 in 5 minutes, #85), retries included: every e2e test runs as the one shared admin from
// 127.0.0.1, so locking it or using up the address's failure budget fails the parallel suite.
async function logIn(page: Page, username: string, password: string) {
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

test('a logged-out visitor is sent to the login page and back after logging in', async ({
  page,
}) => {
  await page.goto('/reports')
  await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)reports$/)

  await logIn(page, E2E_USER, 'not-the-password')
  await expect(page.getByText('Wrong username or password.')).toBeVisible()

  await logIn(page, E2E_USER, E2E_PASSWORD)
  await expect(page).toHaveURL('/reports')
  await expect(page.getByRole('button', { name: E2E_USER, exact: true })).toBeVisible()
})

// An unknown user gets the same answer as a wrong password, so the message never tells which part
// was wrong. Starts from a fresh page, so no earlier alert can satisfy the check.
test('an unknown user gets the same message as a wrong password', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText('Wrong username or password.')).toHaveCount(0)

  const answered = page.waitForResponse('**/api/auth/login')
  await logIn(page, 'nobody', 'not-the-password')
  await answered

  await expect(page.getByText('Wrong username or password.')).toBeVisible()
  await expect(page).toHaveURL(/\/login/)
})

test('logging out returns to the login page and the back button shows no data', async ({
  page,
}) => {
  await page.goto('/login')
  await logIn(page, E2E_USER, E2E_PASSWORD)
  await expect(page).toHaveURL('/')

  await page.getByRole('button', { name: E2E_USER, exact: true }).click()
  await page.getByText('Log out').click()
  await expect(page).toHaveURL('/login')

  await page.goBack()
  await expect(page.getByRole('heading', { name: 'Multi-agent stock analysis' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: E2E_USER, exact: true })).toHaveCount(0)
})
