import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test'
import { csrfHeaders, TRADE_DATE } from './support'

// The platform's analysis defaults are one shared row: the tests run serially and each puts back
// what it found.
test.describe.configure({ mode: 'serial' })

interface Defaults {
  llmProvider: string
  deepThinkLlm: string
  quickThinkLlm: string
  maxDebateRounds: number
  maxRiskDiscussRounds: number
  version: number | null
}

interface Provider {
  id: string
  deepModels: { id: string }[]
  quickModels: { id: string }[]
}

async function readDefaults(request: APIRequestContext): Promise<Defaults> {
  const response = await request.get('/api/settings/analysis-defaults')
  expect(response.ok(), await response.text()).toBe(true)
  return (await response.json()) as Defaults
}

async function saveDefaults(request: APIRequestContext, values: Partial<Defaults>) {
  const current = await readDefaults(request)
  const response = await request.put('/api/settings/analysis-defaults', {
    headers: await csrfHeaders(request),
    data: { ...current, ...values, version: current.version ?? undefined },
  })
  expect(response.ok(), await response.text()).toBe(true)
}

async function providers(request: APIRequestContext): Promise<Provider[]> {
  const response = await request.get('/api/catalog')
  expect(response.ok(), await response.text()).toBe(true)
  return ((await response.json()) as { providers: Provider[] }).providers
}

function modelIds(provider: Provider) {
  const deep = provider.deepModels.map((model) => model.id)
  const quick = provider.quickModels.map((model) => model.id)
  return { deep, quick }
}

async function startAs(request: APIRequestContext, ticker: string, extra: object = {}) {
  return request.post('/api/analyses', {
    headers: await csrfHeaders(request),
    data: { ticker, tradeDate: TRADE_DATE, analysts: ['MARKET'], ...extra },
  })
}

async function loggedInAnalyst(browser: Browser, admin: APIRequestContext) {
  const username = `e2e-defaults-${Date.now()}`
  const newPassword = 'a-brand-new-password-1'
  const roles = (await (await admin.get('/api/roles')).json()) as { id: string; name: string }[]
  const role = roles.find((candidate) => candidate.name === 'ANALYST')
  expect(role).toBeDefined()
  const created = await admin.post('/api/users', {
    headers: await csrfHeaders(admin),
    data: { username, roleIds: [role!.id] },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const { oneTimePassword } = (await created.json()) as { oneTimePassword: string }

  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  const page = await context.newPage()
  await page.goto('/login')
  await page.getByPlaceholder('Username').fill(username)
  await page.getByPlaceholder('Password').fill(oneTimePassword)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/password\?redirect=\/$/)
  await page.getByPlaceholder('Current password').fill(oneTimePassword)
  await page.getByPlaceholder('New password', { exact: true }).fill(newPassword)
  await page.getByPlaceholder('Repeat new password').fill(newPassword)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page).toHaveURL('/')
  return { context, page }
}

async function waitCompleted(request: APIRequestContext, id: string) {
  await expect
    .poll(
      async () =>
        ((await (await request.get(`/api/analyses/${id}`)).json()) as { status: string }).status,
      { timeout: 45_000 },
    )
    .toBe('COMPLETED')
}

async function specOf(request: APIRequestContext, id: string) {
  const response = await request.get(`/api/analyses/${id}`)
  expect(response.ok(), `${id}: ${await response.text()}`).toBe(true)
  return ((await response.json()) as { spec: Defaults }).spec
}

async function openNewAnalysis(page: Page) {
  await page.goto('/analyses/new')
  await expect(page.getByText(/This provider needs its key/)).toBeVisible({ timeout: 30_000 })
}

test('the admin saves the analysis defaults on the card and finds them after a reload', async ({
  page,
  request,
}) => {
  const original = await readDefaults(request)
  try {
    await page.goto('/settings')
    const card = page
      .locator('.n-space', { has: page.getByRole('heading', { name: 'Analysis defaults' }) })
      .last()
    await expect(card.getByRole('button', { name: 'Save' })).toBeEnabled({ timeout: 30_000 })
    // Naive UI's select carries no combobox or option roles.
    await card.locator('.n-base-selection').nth(1).click()
    const option = page
      .locator('.n-base-select-option:not(.n-base-select-option--selected)')
      .first()
    const chosen = ((await option.innerText()) as string).split(' — ')[0]
    await option.click()
    const rounds = card.locator('.n-input-number input')
    await rounds.nth(0).fill('4')
    await rounds.nth(1).fill('5')
    await card.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText('Analysis defaults saved.')).toBeVisible()

    await page.reload()
    await expect(rounds.nth(0)).toHaveValue('4')
    await expect(rounds.nth(1)).toHaveValue('5')
    await expect(card.locator('.n-base-selection').nth(1)).toContainText(chosen)
  } finally {
    await saveDefaults(request, original)
  }
})

test('the platform values are what an analyst sees, starts with and finds on the run page', async ({
  page,
  request,
  browser,
}) => {
  const original = await readDefaults(request)
  const openai = (await providers(request)).find((provider) => provider.id === 'openai')!
  const { deep, quick } = modelIds(openai)
  const values = {
    llmProvider: 'openai',
    deepThinkLlm: deep[deep.length - 1],
    quickThinkLlm: quick[quick.length - 1],
    maxDebateRounds: 2,
    maxRiskDiscussRounds: 3,
  }
  const { context, page: analyst } = await loggedInAnalyst(browser, request)
  try {
    await saveDefaults(request, values)

    await openNewAnalysis(analyst)
    await expect(analyst.getByText('Set by the administrator')).toBeVisible()
    const rounds = analyst.locator('.n-input-number input')
    await expect(rounds.first()).toHaveValue('2')
    await expect(rounds.nth(1)).toHaveValue('3')
    await expect(rounds.first()).toBeDisabled()
    await expect(rounds.nth(1)).toBeDisabled()

    await analyst.goto('/settings')
    await expect(analyst.getByText('OPENAI_API_KEY')).toBeVisible()
    await expect(analyst.getByText('Analysis defaults')).toHaveCount(0)

    await openNewAnalysis(analyst)
    await analyst.getByPlaceholder('e.g. NVDA, THYAO.IS, BTC-USD').fill('TSLA')
    await analyst.getByRole('button', { name: 'Start analysis' }).click()
    await expect(analyst).toHaveURL(/\/analyses\/(?!new$)[^/]+$/)
    const id = analyst.url().split('/').pop()!
    const spec = await specOf(analyst.request, id)
    expect(spec).toMatchObject(values)
    await analyst.locator('.n-tabs-tab', { hasText: 'Stats' }).click()
    await expect(analyst.getByText(values.deepThinkLlm, { exact: true }).first()).toBeVisible()
    await waitCompleted(analyst.request, id)
  } finally {
    await context.close()
    await saveDefaults(request, original)
  }
})

test('a request with other values is refused, one without them gets the platform values', async ({
  request,
  browser,
}) => {
  const original = await readDefaults(request)
  const all = await providers(request)
  const other = all.find((provider) => provider.id !== original.llmProvider)!
  const { context, page: analyst } = await loggedInAnalyst(browser, request)
  try {
    const refused = await startAs(analyst.request, 'META', {
      llmProvider: other.id,
      deepThinkLlm: other.deepModels[0].id,
      quickThinkLlm: other.quickModels[0].id,
    })
    expect(refused.status()).toBe(403)
    const body = (await refused.json()) as { errorCode: string; params: { field: string } }
    expect(body.errorCode).toBe('analysis_override_forbidden')
    expect(body.params.field).toBe('llmProvider')

    const started = await startAs(analyst.request, 'META')
    expect(started.status()).toBe(201)
    const { id } = (await started.json()) as { id: string }
    expect(await specOf(analyst.request, id)).toMatchObject({
      llmProvider: original.llmProvider,
      deepThinkLlm: original.deepThinkLlm,
      quickThinkLlm: original.quickThinkLlm,
      maxDebateRounds: original.maxDebateRounds,
      maxRiskDiscussRounds: original.maxRiskDiscussRounds,
    })
    await waitCompleted(analyst.request, id)
  } finally {
    await context.close()
  }
})

test("an admin's override applies to one analysis and leaves the platform values", async ({
  page,
  request,
}) => {
  const before = await readDefaults(request)
  const provider = (await providers(request)).find((candidate) => candidate.id === 'openai')!
  const { deep, quick } = modelIds(provider)
  test.skip(deep.length < 2, 'the catalog lists one deep model only')
  const changed = deep[1]
  await saveDefaults(request, {
    llmProvider: 'openai',
    deepThinkLlm: deep[0],
    quickThinkLlm: quick[0],
    maxDebateRounds: 1,
    maxRiskDiscussRounds: 1,
  })
  const platform = await readDefaults(request)
  const started = await startAs(request, 'AAPL', { deepThinkLlm: changed })
  expect(started.status(), await started.text()).toBe(201)
  const { id } = (await started.json()) as { id: string }
  try {
    expect(await specOf(request, id)).toMatchObject({
      llmProvider: platform.llmProvider,
      deepThinkLlm: changed,
      quickThinkLlm: platform.quickThinkLlm,
    })
    expect(await readDefaults(request)).toEqual(platform)
    await waitCompleted(request, id)

    await openNewAnalysis(page)
    await expect(page.getByRole('button', { name: 'Reset to defaults' })).toBeVisible()
  } finally {
    await saveDefaults(request, before)
  }
})

test('an analyst who applies a preset with other models keeps the platform values', async ({
  request,
  browser,
}) => {
  const original = await readDefaults(request)
  const openai = (await providers(request)).find((provider) => provider.id === 'openai')!
  const { deep, quick } = modelIds(openai)
  const { context, page: analyst } = await loggedInAnalyst(browser, request)
  const name = `E2E models preset ${Date.now()}`
  // A preset is its owner's: the analyst saves it, as the admin's would not be in their list.
  const own = context.request
  const created = await own.post('/api/presets', {
    headers: await csrfHeaders(own),
    data: {
      name,
      values: {
        llmProvider: 'openai',
        deepThinkLlm: deep[0],
        quickThinkLlm: quick[0],
        maxDebateRounds: 4,
        maxRiskDiscussRounds: 4,
        analysts: ['MARKET', 'NEWS'],
      },
    },
  })
  expect(created.ok(), await created.text()).toBe(true)
  const preset = (await created.json()) as { id: string }
  try {
    await saveDefaults(request, {
      llmProvider: 'openai',
      deepThinkLlm: deep[deep.length - 1],
      quickThinkLlm: quick[quick.length - 1],
      maxDebateRounds: 2,
      maxRiskDiscussRounds: 2,
    })
    await openNewAnalysis(analyst)
    await analyst.locator('.n-base-selection', { hasText: 'Load a preset' }).click()
    await analyst.locator('.n-base-select-option', { hasText: name }).click()
    await expect(
      analyst.getByRole('checkbox', { name: 'Sentiment (social media)' }),
    ).not.toBeChecked()
    await expect(analyst.locator('.n-input-number input').first()).toHaveValue('2')
    await expect(analyst.locator('.n-input-number input').nth(1)).toHaveValue('2')
  } finally {
    await own.delete(`/api/presets/${preset.id}`, { headers: await csrfHeaders(own) })
    await context.close()
    await saveDefaults(request, original)
  }
})
