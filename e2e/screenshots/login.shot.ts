import { test } from '@playwright/test'
import { shot } from './support'

test('login page', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('Username').waitFor()
  await shot(page, 'login')
})
