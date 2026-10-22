export type RunView = {
  name: string
  isActive: boolean
  step: string
  subStep: string
  stepSince: number
  tokens: number
  milestones: { at: number; text: string }[]
}

declare module 'claude-code' {
  interface PluginState {
    'agent-progress': { run: RunView | null }
  }
}
