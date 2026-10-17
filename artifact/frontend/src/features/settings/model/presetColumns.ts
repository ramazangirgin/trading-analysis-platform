/** Which preset table columns a user gets: the keys, and whether the name cell can be edited. */
export interface PresetColumnPlan {
  readonly keys: readonly string[]
  readonly nameEditable: boolean
}

/** A read-only user sees the name as text and has no delete column. */
export function presetColumnPlan(readonly: boolean): PresetColumnPlan {
  return readonly
    ? { keys: ['name', 'values', 'updatedAt'], nameEditable: false }
    : { keys: ['name', 'values', 'updatedAt', 'delete'], nameEditable: true }
}
