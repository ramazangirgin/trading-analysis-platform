/** Which preset table columns a user gets: the keys, and whether the name cell can be edited. */
export interface PresetColumnPlan {
  readonly keys: readonly string[]
  readonly nameEditable: boolean
}

/** A read-only user sees the name as text and has no delete column; the list of all users adds the owner. */
export function presetColumnPlan(readonly: boolean, allUsers = false): PresetColumnPlan {
  const keys = readonly
    ? ['name', 'values', 'updatedAt']
    : ['name', 'values', 'updatedAt', 'delete']
  if (allUsers) keys.splice(1, 0, 'owner')
  return { keys, nameEditable: !readonly }
}
