import type { RunEvent } from '@/shared/api/types'

export type AgentStatus = 'pending' | 'in_progress' | 'completed' | 'error'

export interface FeedItem {
  seq: number
  timestamp: string
  kind: 'message' | 'tool_call' | 'tool_result' | 'log'
  title: string
  text: string
  level?: string
}

export interface DebateTurn {
  seq: number
  debate: 'investment' | 'risk'
  speaker: string
  round: number | null
  content: string
}

export interface RunView {
  agents: Record<string, AgentStatus>
  sections: Record<string, string>
  debates: DebateTurn[]
  feed: FeedItem[]
  stats: Record<string, number | null> | null
  decision: { rating: string; raw: string } | null
  finished: { status: string; error?: string; errorType?: string } | null
  lastSeq: number
}

/**
 * The pipeline in upstream's order, grouped as the UI shows it. `debaters` argue in turns
 * (bull ⇄ bear, the three risk views); `judge` closes the debate.
 */
export interface PipelineStage {
  stage: 'analysts' | 'research' | 'trading' | 'risk' | 'portfolio'
  agents: string[]
  debaters?: string[]
  judge?: string
}

export const PIPELINE: PipelineStage[] = [
  {
    stage: 'analysts',
    agents: ['Market Analyst', 'Sentiment Analyst', 'News Analyst', 'Fundamentals Analyst'],
  },
  {
    stage: 'research',
    agents: ['Bull Researcher', 'Bear Researcher', 'Research Manager'],
    debaters: ['Bull Researcher', 'Bear Researcher'],
    judge: 'Research Manager',
  },
  { stage: 'trading', agents: ['Trader'] },
  {
    stage: 'risk',
    agents: ['Aggressive Analyst', 'Conservative Analyst', 'Neutral Analyst'],
    debaters: ['Aggressive Analyst', 'Conservative Analyst', 'Neutral Analyst'],
  },
  { stage: 'portfolio', agents: ['Portfolio Manager'] },
]

/**
 * One status for a stage, from the agents the run reported (analysts it did not select never
 * appear): an error wins, then work in progress, then done once every agent is done.
 */
export function stageStatus(
  agents: Record<string, AgentStatus>,
  names: string[],
): AgentStatus | null {
  const statuses = names.filter((name) => name in agents).map((name) => agents[name]!)
  if (!statuses.length) return null
  if (statuses.includes('error')) return 'error'
  if (statuses.includes('in_progress')) return 'in_progress'
  if (statuses.every((status) => status === 'completed')) return 'completed'
  return statuses.includes('completed') ? 'in_progress' : 'pending'
}

export function emptyRunView(): RunView {
  return {
    agents: {},
    sections: {},
    debates: [],
    feed: [],
    stats: null,
    decision: null,
    finished: null,
    lastSeq: 0,
  }
}

const text = (value: unknown): string => (typeof value === 'string' ? value : '')
const num = (value: unknown): number | null => (typeof value === 'number' ? value : null)

/**
 * Folds one event into the view. Events at or below `lastSeq` are ignored, so replays and
 * reconnects never duplicate anything. Mutates and returns `view`.
 */
export function applyRunEvent(view: RunView, event: RunEvent): RunView {
  if (event.seq <= view.lastSeq) return view
  view.lastSeq = event.seq
  const p = event.payload
  const base = { seq: event.seq, timestamp: event.timestamp }
  switch (event.type) {
    case 'agent_status':
      view.agents[text(p.agent)] = text(p.status) as AgentStatus
      break
    case 'report_section':
      view.sections[text(p.section)] = text(p.markdown)
      break
    case 'debate':
      view.debates.push({
        seq: event.seq,
        debate: p.debate === 'risk' ? 'risk' : 'investment',
        speaker: text(p.speaker),
        round: num(p.round),
        content: text(p.content),
      })
      break
    case 'message': {
      const toolCalls = Array.isArray(p.tool_calls) ? (p.tool_calls as unknown[]).map(text) : []
      view.feed.push({
        ...base,
        kind: 'message',
        title: text(p.name) || text(p.role),
        text: text(p.content) || toolCalls.map((name) => `→ ${name}()`).join('\n'),
      })
      break
    }
    case 'tool_call':
      view.feed.push({ ...base, kind: 'tool_call', title: text(p.tool), text: text(p.args) })
      break
    case 'tool_result':
      view.feed.push({
        ...base,
        kind: 'tool_result',
        title: `${text(p.tool)} · ${num(p.duration_ms) ?? '?'} ms`,
        text: text(p.error) || text(p.output),
        level: p.error ? 'ERROR' : undefined,
      })
      break
    case 'log':
      view.feed.push({
        ...base,
        kind: 'log',
        title: text(p.logger),
        text: text(p.message),
        level: text(p.level),
      })
      break
    case 'stats':
      view.stats = {
        llmCalls: num(p.llm_calls),
        toolCalls: num(p.tool_calls),
        tokensIn: num(p.tokens_in),
        tokensOut: num(p.tokens_out),
        costUsd: num(p.cost_usd),
        elapsedS: num(p.elapsed_s),
      }
      break
    case 'decision':
      view.decision = { rating: text(p.rating), raw: text(p.raw) }
      break
    case 'run_finished':
      view.finished = {
        status: text(p.status),
        error: text(p.error) || undefined,
        errorType: text(p.error_type) || text(p.error_code) || undefined,
      }
      break
  }
  return view
}
