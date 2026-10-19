import { expect, test } from '@playwright/test'
import { csrfHeaders } from '../tests/support'
import { shot } from './support'

// README, "3. Log in": the Roles page with the built-in roles and one custom role that has a user.
test('roles page', async ({ page, request }) => {
  const headers = await csrfHeaders(request)

  const role = await request.post('/api/roles', {
    headers,
    data: {
      name: 'Read-only analyst',
      description: 'Reads analyses and presets, starts nothing',
      permissions: ['analysis:read', 'preset:read'],
    },
  })
  expect(role.ok(), await role.text()).toBe(true)
  const { id } = (await role.json()) as { id: string }
  const user = await request.post('/api/users', {
    headers,
    data: { username: 'nora', roleIds: [id] },
  })
  expect(user.ok(), await user.text()).toBe(true)

  await page.goto('/admin/roles')
  await expect(page.getByRole('row', { name: /Read-only analyst/ })).toContainText('preset:read')
  await shot(page, 'roles')
})
