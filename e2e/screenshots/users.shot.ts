import { expect, test } from '@playwright/test'
import { csrfHeaders } from '../tests/support'
import { shot } from './support'

// README, "3. Log in": the Users page with an active ANALYST and a disabled VIEWER.
test('users page', async ({ page, request }) => {
  const headers = await csrfHeaders(request)
  const roles = (await (await request.get('/api/roles')).json()) as { id: string; name: string }[]
  const roleId = (name: string) => roles.find((role) => role.name === name)!.id

  async function createUser(username: string, role: string) {
    const created = await request.post('/api/users', {
      headers,
      data: { username, roleIds: [roleId(role)] },
    })
    expect(created.ok(), await created.text()).toBe(true)
    return ((await created.json()) as { user: { id: string } }).user.id
  }

  await createUser('maria', 'ANALYST')
  const viewer = await createUser('tom', 'VIEWER')
  const disabled = await request.patch(`/api/users/${viewer}`, {
    headers,
    data: { enabled: false },
  })
  expect(disabled.ok(), await disabled.text()).toBe(true)

  await page.goto('/admin/users')
  await expect(page.getByRole('row', { name: /tom/ })).toContainText('Disabled')
  await shot(page, 'users')
})
