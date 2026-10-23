import type { Analysis } from '@/shared/api/types'

export type VisibilityControl =
  { kind: 'switch'; disabled: boolean } | { kind: 'makePrivate' } | { kind: 'none' }

/** What the detail page offers for the analysis's visibility; the backend decides `canChangeVisibility`. */
export function visibilityControl(
  analysis: Pick<Analysis, 'owner' | 'status' | 'visibility' | 'canChangeVisibility'>,
  userId: string | null,
): VisibilityControl {
  if (!analysis.canChangeVisibility) return { kind: 'none' }
  const isOwner = userId !== null && analysis.owner?.id === userId
  if (!isOwner) return { kind: 'makePrivate' }
  return {
    kind: 'switch',
    disabled: analysis.visibility === 'PRIVATE' && analysis.status !== 'COMPLETED',
  }
}
