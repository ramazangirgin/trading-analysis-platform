import { describe, expect, it } from 'vitest'
import { visibilityControl } from './visibility'

const base = {
  owner: { id: 'u1', username: 'maria' },
  status: 'COMPLETED' as const,
  visibility: 'PRIVATE' as const,
  canChangeVisibility: true,
}

describe('visibilityControl', () => {
  it('gives the owner a switch for a completed private analysis', () => {
    expect(visibilityControl(base, 'u1')).toEqual({ kind: 'switch', disabled: false })
  })

  it('disables the switch while a private analysis is not completed', () => {
    expect(visibilityControl({ ...base, status: 'RUNNING' }, 'u1')).toEqual({
      kind: 'switch',
      disabled: true,
    })
  })

  it('keeps the switch enabled for a public analysis', () => {
    expect(visibilityControl({ ...base, visibility: 'PUBLIC' }, 'u1')).toEqual({
      kind: 'switch',
      disabled: false,
    })
  })

  it('gives another user with the right a make-private button', () => {
    expect(visibilityControl({ ...base, visibility: 'PUBLIC' }, 'admin')).toEqual({
      kind: 'makePrivate',
    })
  })

  it('shows nothing without canChangeVisibility', () => {
    expect(visibilityControl({ ...base, canChangeVisibility: false }, 'u1')).toEqual({
      kind: 'none',
    })
    expect(
      visibilityControl({ ...base, visibility: 'PUBLIC', canChangeVisibility: false }, 'u2'),
    ).toEqual({ kind: 'none' })
  })
})
