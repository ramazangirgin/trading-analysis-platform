import { describe, expect, it } from 'vitest'
import { visibilityControl } from './visibility'

const base = {
  status: 'COMPLETED' as const,
  canChangeVisibility: true,
  allowedVisibilities: ['PUBLIC' as const, 'PARTIAL' as const],
}

function enabled(control: ReturnType<typeof visibilityControl>): string[] {
  if (control.kind !== 'select') return []
  return control.options.filter((o) => !o.disabled).map((o) => o.value)
}

describe('visibilityControl', () => {
  it('offers the owner of a private completed analysis Public and Partial', () => {
    const control = visibilityControl(base)
    expect(control.kind === 'select' && control.options.map((o) => o.value)).toEqual([
      'PRIVATE',
      'PUBLIC',
      'PARTIAL',
    ])
    expect(enabled(control)).toEqual(['PUBLIC', 'PARTIAL'])
  })

  it('disables every other option of a private analysis that is still running', () => {
    const control = visibilityControl({ ...base, status: 'RUNNING', allowedVisibilities: [] })
    expect(enabled(control)).toEqual([])
    expect(control).toMatchObject({
      options: [
        { value: 'PRIVATE', disabledReason: 'notCompleted' },
        { value: 'PUBLIC', disabledReason: 'notCompleted' },
        { value: 'PARTIAL', disabledReason: 'notCompleted' },
      ],
    })
  })

  it('offers Private and Partial for a public analysis', () => {
    expect(
      enabled(visibilityControl({ ...base, allowedVisibilities: ['PRIVATE', 'PARTIAL'] })),
    ).toEqual(['PRIVATE', 'PARTIAL'])
  })

  it('offers Private and Public for a partial analysis', () => {
    expect(
      enabled(visibilityControl({ ...base, allowedVisibilities: ['PRIVATE', 'PUBLIC'] })),
    ).toEqual(['PRIVATE', 'PUBLIC'])
  })

  it("gives an admin on another user's partial analysis Public and Private only", () => {
    const control = visibilityControl({ ...base, allowedVisibilities: ['PRIVATE', 'PUBLIC'] })
    expect(control).toMatchObject({
      options: [
        { value: 'PRIVATE', disabled: false },
        { value: 'PUBLIC', disabled: false },
        { value: 'PARTIAL', disabled: true, disabledReason: null },
      ],
    })
  })

  it('shows nothing without canChangeVisibility', () => {
    expect(visibilityControl({ ...base, canChangeVisibility: false })).toEqual({ kind: 'none' })
  })
})
