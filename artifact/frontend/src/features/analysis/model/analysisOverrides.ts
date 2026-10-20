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

export function presetFieldsToApply(
  values: Record<string, unknown>,
  presetFields: readonly string[],
  mayOverride: boolean,
): Record<string, unknown> {
  return Object.fromEntries(
    presetFields
      .filter((f) => f in values && (mayOverride || !isModelField(f)))
      .map((f) => [f, values[f]]),
  )
}

export function presetFieldsToSave(
  form: Record<string, unknown>,
  presetFields: readonly string[],
  mayOverride: boolean,
): Record<string, unknown> {
  return Object.fromEntries(
    presetFields.filter((f) => mayOverride || !isModelField(f)).map((f) => [f, form[f]]),
  )
}
