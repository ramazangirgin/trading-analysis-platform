import { expect, test, type Page } from '@playwright/test'
import { E2E_PASSWORD, E2E_USER } from './support'

// A fresh context, logged out, and the login through the form: the shared session of the other
// tests must stay alive, and logging out here would end it.
test.use({ storageState: { cookies: [], origins: [] } })

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
  const alert = page.getByText('Wrong username or password.')
  await expect(alert).toBeVisible()

  // An unknown user gets the same answer, so the message never tells which part was wrong.
  await page.getByPlaceholder('Username').fill('nobody')
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(alert).toBeVisible()

  await logIn(page, E2E_USER, E2E_PASSWORD)
  await expect(page).toHaveURL('/reports')
  await expect(page.getByRole('button', { name: E2E_USER })).toBeVisible()
})

test('logging out returns to the login page and the back button shows no data', async ({
  page,
}) => {
  await page.goto('/login')
  await logIn(page, E2E_USER, E2E_PASSWORD)
  await expect(page).toHaveURL('/')

  await page.getByRole('button', { name: E2E_USER }).click()
  await page.getByText('Log out').click()
  await expect(page).toHaveURL('/login')

  await page.goBack()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByPlaceholder('Username')).toBeVisible()
  await expect(page.getByRole('button', { name: E2E_USER })).toHaveCount(0)
})
