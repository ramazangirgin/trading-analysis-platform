import type { Analysis, Visibility } from '@/shared/api/types'

const ORDER: Visibility[] = ['PRIVATE', 'PUBLIC', 'PARTIAL']

export interface VisibilityOption {
  value: Visibility
  disabled: boolean
  disabledReason: 'notCompleted' | null
}

export type VisibilityControl = { kind: 'select'; options: VisibilityOption[] } | { kind: 'none' }

/**
 * What the detail page offers for the analysis's visibility: the backend decides who may change it
 * (`canChangeVisibility`) and to which values (`allowedVisibilities`).
 */
export function visibilityControl(
  analysis: Pick<Analysis, 'status' | 'canChangeVisibility' | 'allowedVisibilities'>,
): VisibilityControl {
  if (!analysis.canChangeVisibility) return { kind: 'none' }
  const options = ORDER.map((value) => {
    const disabled = !analysis.allowedVisibilities.includes(value)
    return {
      value,
      disabled,
      disabledReason:
        disabled && analysis.status !== 'COMPLETED' ? ('notCompleted' as const) : null,
    }
  })
  return { kind: 'select', options }
}
