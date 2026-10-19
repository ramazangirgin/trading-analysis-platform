import { test } from '@playwright/test'
import { E2E_USER } from '../tests/support'
import { shot } from './support'

test('change-password page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: E2E_USER, exact: true }).click()
  await page.getByText('Change password').click()
  await page.getByPlaceholder('Current password').waitFor()
  await shot(page, 'change-password')
})
