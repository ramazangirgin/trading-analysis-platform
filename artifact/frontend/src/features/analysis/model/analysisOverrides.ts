import type { PresetValues } from '@/shared/api/types'

export const MODEL_FIELDS = [
  'llmProvider',
  'deepThinkLlm',
  'quickThinkLlm',
  'maxDebateRounds',
  'maxRiskDiscussRounds',
] as const

export type ModelField = (typeof MODEL_FIELDS)[number]

export interface ModelValues {
  llmProvider: string | null
  deepThinkLlm: string | null
  quickThinkLlm: string | null
  maxDebateRounds: number
  maxRiskDiscussRounds: number
}

const isModelField = (field: string): field is ModelField =>
  (MODEL_FIELDS as readonly string[]).includes(field)

export function overriddenFields(
  form: ModelValues,
  defaults: ModelValues,
  mayOverride: boolean,
): Partial<{ [K in ModelField]: NonNullable<ModelValues[K]> }> {
  if (!mayOverride) return {}
  return Object.fromEntries(
    MODEL_FIELDS.filter((f) => form[f] !== null && form[f] !== defaults[f]).map((f) => [
      f,
      form[f],
    ]),
  )
}

type PresetField = keyof PresetValues

export function presetFieldsToApply(
  values: PresetValues,
  presetFields: readonly PresetField[],
  mayOverride: boolean,
): PresetValues {
  return Object.fromEntries(
    presetFields
      .filter((f) => values[f] !== undefined && (mayOverride || !isModelField(f)))
      .map((f) => [f, values[f]]),
  ) as PresetValues
}

export function presetFieldsToSave(
  form: { [K in PresetField]?: PresetValues[K] | null },
  presetFields: readonly PresetField[],
  mayOverride: boolean,
): PresetValues {
  return Object.fromEntries(
    presetFields
      .filter((f) => mayOverride || !isModelField(f))
      .map((f) => [f, form[f] ?? undefined]),
  ) as PresetValues
}
