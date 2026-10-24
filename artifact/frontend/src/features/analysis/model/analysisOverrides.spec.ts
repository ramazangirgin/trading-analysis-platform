import { describe, expect, it } from 'vitest'
import {
  overriddenFields,
  presetFieldsToApply,
  presetFieldsToSave,
  type ModelValues,
} from './analysisOverrides'

const DEFAULTS: ModelValues = {
  llmProvider: 'deepseek',
  deepThinkLlm: 'deepseek-v4-pro',
  quickThinkLlm: 'deepseek-v4-flash',
  maxDebateRounds: 2,
  maxRiskDiscussRounds: 3,
}
const PRESET_FIELDS = [
  'assetType',
  'llmProvider',
  'deepThinkLlm',
  'maxDebateRounds',
  'outputLanguage',
] as const

describe('overriddenFields', () => {
  it('carries no field without the permission', () => {
    expect(overriddenFields({ ...DEFAULTS, deepThinkLlm: 'other' }, DEFAULTS, false)).toEqual({})
  })

  it('carries only the differing fields with the permission', () => {
    const form = { ...DEFAULTS, deepThinkLlm: 'other', maxDebateRounds: 4 }
    expect(overriddenFields(form, DEFAULTS, true)).toEqual({
      deepThinkLlm: 'other',
      maxDebateRounds: 4,
    })
  })

  it('carries none after a reset to the platform values', () => {
    expect(overriddenFields({ ...DEFAULTS }, DEFAULTS, true)).toEqual({})
  })
})

describe('presetFieldsToApply', () => {
  const values = {
    assetType: 'CRYPTO',
    llmProvider: 'openai',
    deepThinkLlm: 'x',
    maxDebateRounds: 5,
  }

  it('applies every stored field with the permission', () => {
    expect(presetFieldsToApply(values, PRESET_FIELDS, true)).toEqual(values)
  })

  it('leaves the model and round fields out without it', () => {
    expect(presetFieldsToApply(values, PRESET_FIELDS, false)).toEqual({ assetType: 'CRYPTO' })
  })
})

describe('presetFieldsToSave', () => {
  const form = { ...DEFAULTS, assetType: 'STOCK', outputLanguage: 'English' }

  it('saves all preset fields with the permission', () => {
    expect(presetFieldsToSave(form, PRESET_FIELDS, true)).toEqual({
      assetType: 'STOCK',
      llmProvider: 'deepseek',
      deepThinkLlm: 'deepseek-v4-pro',
      maxDebateRounds: 2,
      outputLanguage: 'English',
    })
  })

  it('saves the other fields only without it', () => {
    expect(presetFieldsToSave(form, PRESET_FIELDS, false)).toEqual({
      assetType: 'STOCK',
      outputLanguage: 'English',
    })
  })
})
