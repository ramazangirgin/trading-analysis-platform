import { atom, read, update } from 'claude-code'
import type { Hook, Register } from 'claude-code'

import type { RunView } from '../types'

const PANE = 'agent-progress'
const POLL_MS = 3000
// Milestones that say where the loop is going, not what happened.
const QUIET = /^#\d+: (next step|waiting for)/
const run = atom({ plugin: 'agent-progress', key: 'run' } as const, null)

type Event = { at: number; kind: string; parent: string; name: string; text: string }

const parse = (tsv: string): Event[] =>
  tsv
    .split('\n')
    .filter(Boolean)
    .map(line => {
      const [at, kind, parent, name, ...text] = line.split('\t')
      return { at: Number(at), kind, parent, name, text: text.join('\t') }
    })

const summarise = (name: string, events: Event[], isActive: boolean): RunView => {
  const steps: Event[] = []
  const subSteps: Event[] = []
  let tokens = 0
  for (const e of events) {
    if (e.kind === 'begin' && e.parent === '') steps.push(e)
    if (e.kind === 'end' && e.parent === '') {
      const at = steps.findLastIndex(s => s.name === e.name)
      if (at >= 0) steps.splice(at, 1)
    }
    if (e.kind === 'begin' && e.parent !== '') subSteps.push(e)
    if (e.kind === 'end' && e.parent !== '') {
      const at = subSteps.findLastIndex(s => s.name === e.name && s.parent === e.parent)
      if (at >= 0) subSteps.splice(at, 1)
    }
    if (e.kind === 'usage') tokens += Number(/tokens=(\d+)/.exec(e.text)?.[1] ?? 0)
  }
  const step = steps.at(-1)
  const subStep = subSteps.findLast(s => s.parent === step?.name)
  return {
    name,
    isActive,
    step: step?.name ?? '',
    subStep: subStep?.name ?? '',
    stepSince: step?.at ?? 0,
    tokens,
    milestones: events
      .filter(e => e.kind === 'milestone' && !QUIET.test(e.text))
      .slice(-12)
      .map(e => ({ at: e.at, text: e.text })),
  }
}

const tokensText = (n: number) =>
  n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e3 ? `${Math.round(n / 1e3)}k` : `${n}`
const minutes = (seconds: number) => `${Math.max(0, Math.round(seconds / 60))}m`
const clock = (at: number) => new Date(at * 1000).toTimeString().slice(0, 5)

type Dollar = Parameters<Hook<'session.start'>>[0]

// The state of every run is in the main repository's .git, shared by the worktrees.
let agentDir: string | undefined
const seen = new Map<string, { mtimeMs: number; milestones: number }>()
let newest = { mtimeMs: 0, name: '' }

async function poll($: Dollar) {
  agentDir ??= await $.process
    .run(['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'])
    .then(r => (r.exitCode === 0 ? `${r.stdout.trim()}/agent` : ''))
  if (!agentDir) return
  const dirs = await $.fs.list(agentDir).catch(() => [])
  for (const dir of dirs) {
    const tsv = `${agentDir}/${dir.name}/progress.tsv`
    const stat = await $.fs.stat(tsv).catch(() => undefined)
    if (!stat) continue
    if (stat.mtimeMs > newest.mtimeMs) newest = { mtimeMs: stat.mtimeMs, name: dir.name }
    if (seen.get(dir.name)?.mtimeMs === stat.mtimeMs) continue
    const events = parse(await $.fs.read(tsv))
    const milestones = events.filter(e => e.kind === 'milestone' && !QUIET.test(e.text))
    const before = seen.get(dir.name)
    seen.set(dir.name, { mtimeMs: stat.mtimeMs, milestones: milestones.length })
    // The first look at a run is its baseline: its history is not announced.
    for (const m of before ? milestones.slice(before.milestones) : []) {
      $.ui.toast(`${dir.name.split('-')[0]}: ${m.text}`)
    }
  }
  if (!newest.name) return
  const pid = await $.fs.read(`${agentDir}/${newest.name}/run.pid`).catch(() => '')
  const isActive = pid.trim() !== '' && (await $.process.run(['kill', '-0', pid.trim()])).exitCode === 0
  const view = summarise(newest.name, parse(await $.fs.read(`${agentDir}/${newest.name}/progress.tsv`)), isActive)
  await update($, run, () => view)
  if (!isActive) return $.ui.status(undefined)
  const now = await $.clock.now()
  const where = view.subStep ? `${view.step} ${view.subStep}` : view.step
  $.ui.status(`#${view.name.split('-')[0]} ${where} · ${minutes(now / 1000 - view.stepSince)} · ${tokensText(view.tokens)} tok`)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'agent-progress',
      description: 'Show the active agent run in a pane',
    })
    $.clock.every(POLL_MS, () => poll($).catch(() => undefined))
    return next(e)
  })

  on('command.run', { command: 'agent-progress' }, async $ => {
    await $.ui.open({ id: PANE, title: 'Agent run' })
    return { text: 'Agent run pane opened.' }
  })

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const { Box, Text } = $.ui.resolve(e)
    const view: RunView | null = await read($, run)
    if (!view) return <Text dimColor>No agent run found.</Text>
    const room = Math.max(1, (e.viewport?.rows ?? 24) - 6)
    return (
      <Box flexDirection="column">
        <Text bold>{view.name}</Text>
        <Text dimColor={!view.isActive}>
          {view.isActive ? 'running' : 'not running'} · {view.step || 'no step'}
          {view.subStep ? ` · ${view.subStep}` : ''} · {tokensText(view.tokens)} tokens
        </Text>
        {view.milestones.slice(-room).map(m => (
          <Text>
            {clock(m.at)} {m.text}
          </Text>
        ))}
      </Box>
    )
  })
}
