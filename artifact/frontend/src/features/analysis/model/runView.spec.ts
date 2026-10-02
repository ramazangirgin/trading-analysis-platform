import { describe, expect, it } from 'vitest'
import type { RunEvent } from '@/shared/api/types'
import { applyRunEvent, emptyRunView, stageStatus } from './runView'

let seq = 0
const event = (type: string, payload: Record<string, unknown>): RunEvent => ({
  seq: ++seq,
  timestamp: '2026-09-29T10:00:00Z',
  type,
  payload,
})

describe('applyRunEvent', () => {
  it('builds the run view from the protocol events', () => {
    seq = 0
    const events = [
      event('agent_status', { agent: 'Market Analyst', status: 'in_progress' }),
      event('message', { role: 'ai', name: null, content: '', tool_calls: ['get_stock_data'] }),
      event('tool_call', { tool: 'get_stock_data', args: '{"symbol":"NVDA"}' }),
      event('tool_result', { tool: 'get_stock_data', duration_ms: 120, error: null, output: 'ok' }),
      event('report_section', {
        section: 'market_report',
        agent: 'Market Analyst',
        markdown: '# M',
      }),
      event('agent_status', { agent: 'Market Analyst', status: 'completed' }),
      event('debate', { debate: 'investment', speaker: 'bull', round: 1, content: 'Buy.' }),
      event('stats', {
        llm_calls: 3,
        tool_calls: 1,
        tokens_in: 10,
        tokens_out: 5,
        cost_usd: null,
        elapsed_s: 4.2,
      }),
      event('decision', { rating: 'Overweight', raw: '**Rating**: Overweight' }),
      event('run_finished', { status: 'completed', report_dir: '/x' }),
    ]

    const view = events.reduce(applyRunEvent, emptyRunView())

    expect(view.agents).toEqual({ 'Market Analyst': 'completed' })
    expect(view.sections).toEqual({ market_report: '# M' })
    expect(view.feed.map((item) => item.kind)).toEqual(['message', 'tool_call', 'tool_result'])
    expect(view.feed[0]?.text).toBe('→ get_stock_data()')
    expect(view.debates).toEqual([
      { seq: 7, debate: 'investment', speaker: 'bull', round: 1, content: 'Buy.' },
    ])
    expect(view.stats?.llmCalls).toBe(3)
    expect(view.decision).toEqual({ rating: 'Overweight', raw: '**Rating**: Overweight' })
    expect(view.finished).toEqual({ status: 'completed', error: undefined, errorType: undefined })
    expect(view.lastSeq).toBe(10)
  })

  it('ignores events it has already seen', () => {
    const view = emptyRunView()
    const log = { seq: 1, timestamp: 't', type: 'log', payload: { level: 'INFO', message: 'x' } }

    applyRunEvent(view, log)
    applyRunEvent(view, log)

    expect(view.feed).toHaveLength(1)
  })

  it('keeps the failure details of a run', () => {
    const view = applyRunEvent(emptyRunView(), {
      seq: 1,
      timestamp: 't',
      type: 'run_finished',
      payload: { status: 'error', error_code: 'runner_died', error: 'Runner exited with code 137' },
    })

    expect(view.finished).toEqual({
      status: 'error',
      error: 'Runner exited with code 137',
      errorType: 'runner_died',
    })
  })
})

describe('stageStatus', () => {
  const analysts = ['Market Analyst', 'News Analyst']

  it('ignores agents the run did not report', () => {
    expect(stageStatus({}, analysts)).toBeNull()
    expect(stageStatus({ 'Market Analyst': 'completed' }, analysts)).toBe('completed')
  })

  it('is pending, running, done or failed as a whole', () => {
    expect(stageStatus({ 'Market Analyst': 'pending', 'News Analyst': 'pending' }, analysts)).toBe(
      'pending',
    )
    expect(
      stageStatus({ 'Market Analyst': 'completed', 'News Analyst': 'pending' }, analysts),
    ).toBe('in_progress')
    expect(
      stageStatus({ 'Market Analyst': 'completed', 'News Analyst': 'in_progress' }, analysts),
    ).toBe('in_progress')
    expect(
      stageStatus({ 'Market Analyst': 'error', 'News Analyst': 'in_progress' }, analysts),
    ).toBe('error')
  })
})
