import { expect, test } from '@playwright/test'
import { E2E_PASSWORD, E2E_USER } from './support'

// Runs as the shared admin, whose password must not change: the other tests share its session and a
// change would log them out. The successful change and the forced flow are covered by the backend
// integration tests and Vitest.

test('the user menu opens the change-password page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: E2E_USER }).click()
  await page.getByText('Change password').click()

  await expect(page).toHaveURL('/password')
  await expect(page.locator('.n-card-header__main', { hasText: 'Change password' })).toBeVisible()
  await expect(page.getByPlaceholder('Current password')).toBeVisible()
})

test('a wrong current password shows the message next to the field and stays on the page', async ({
  page,
}) => {
  await page.goto('/password')
  await page.getByPlaceholder('Current password').fill('not-the-password')
  await page.getByPlaceholder('New password', { exact: true }).fill('another-new-password')
  await page.getByPlaceholder('Repeat new password').fill('another-new-password')

  const answered = page.waitForResponse('**/api/auth/password')
  await page.getByRole('button', { name: 'Change password' }).click()
  expect((await answered).status()).toBe(400)

  await expect(page.getByText('The current password is wrong.')).toBeVisible()
  await expect(page).toHaveURL('/password')
})

test("a weak new password shows the policy's reasons", async ({ page, request }) => {
  await page.goto('/password')
  await page.getByPlaceholder('Current password').fill(E2E_PASSWORD)
  await page.getByPlaceholder('New password', { exact: true }).fill('short')
  await page.getByPlaceholder('Repeat new password').fill('short')

  const answered = page.waitForResponse('**/api/auth/password')
  await page.getByRole('button', { name: 'Change password' }).click()
  expect((await answered).status()).toBe(400)

  await expect(page.getByText('At least 12 characters.')).toBeVisible()
  await expect(page).toHaveURL('/password')

  // Nothing changed: a successful change would have ended the shared session the other tests use.
  // (No login here: it would end that session too. The backend integration tests check the stored
  // password.)
  expect((await request.get('/api/auth/me')).ok()).toBe(true)
})

test('a repeat that does not match is caught in the form without a request', async ({ page }) => {
  let requested = false
  await page.route('**/api/auth/password', (route) => {
    requested = true
    return route.abort()
  })

  await page.goto('/password')
  await page.getByPlaceholder('Current password').fill('whatever')
  await page.getByPlaceholder('New password', { exact: true }).fill('one-new-password')
  await page.getByPlaceholder('Repeat new password').fill('another-new-password')
  await page.getByRole('button', { name: 'Change password' }).click()

  await expect(page.getByText('The passwords do not match.')).toBeVisible()
  expect(requested).toBe(false)
})
