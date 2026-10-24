import { expect, test } from '@playwright/test'
import { shot } from './support'
import { csrfHeaders, loginAsNewUser } from '../tests/support'

// README, "5. Start an analysis": Settings → Presets as the admin with All users on, two users' presets.
test('presets', async ({ page, request, browser }) => {
  const username = 'maria-analyst'
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const created = await request.post('/api/users', {
    headers: await csrfHeaders(request),
    data: { username, roleIds: [roles.find((role) => role.name === 'ANALYST')!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const { page: analystPage, context } = await loginAsNewUser(browser, username, oneTimePassword)
  const analystRequest = context.request
  const saved = await analystRequest.post('/api/presets', {
    headers: await csrfHeaders(analystRequest),
    data: {
      name: 'Quick look',
      values: { analysts: ['market', 'news'], maxDebateRounds: 1, maxRiskDiscussRounds: 1 },
    },
  })
  expect(saved.ok(), await saved.text()).toBe(true)
  await analystPage.close()
  await context.close()

  const own = await request.post('/api/presets', {
    headers: await csrfHeaders(request),
    data: {
      name: 'Deep dive',
      values: {
        llmProvider: 'openai',
        deepThinkLlm: 'gpt-5.4-mini',
        quickThinkLlm: 'gpt-5.4-mini',
        analysts: ['market', 'social', 'news', 'fundamentals'],
        maxDebateRounds: 3,
        maxRiskDiscussRounds: 2,
      },
    },
  })
  expect(own.ok(), await own.text()).toBe(true)

  await page.goto('/settings')
  await page.getByRole('switch').click()
  await expect(page.getByRole('columnheader', { name: 'Owner' })).toBeVisible()
  await expect(page.getByRole('row', { name: /maria-analyst/ })).toBeVisible()
  const heading = page.getByRole('heading', { name: 'Presets' })
  await expect(heading).toBeVisible()
  const box = (await heading.boundingBox())!
  const y = Math.max(0, box.y + (await page.evaluate(() => window.scrollY)) - 60)
  await shot(page, 'presets', undefined, { x: 0, y, width: 1440, height: 420 })
})
