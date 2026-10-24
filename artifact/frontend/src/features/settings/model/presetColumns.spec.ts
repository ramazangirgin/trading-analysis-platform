import { describe, expect, it } from 'vitest'
import { presetColumnPlan } from './presetColumns'

describe('presetColumnPlan', () => {
  it('lets a user with the permission rename and delete', () => {
    expect(presetColumnPlan(false)).toEqual({
      keys: ['name', 'values', 'updatedAt', 'delete'],
      nameEditable: true,
    })
  })

  it('shows a read-only user the name as text and no delete column', () => {
    expect(presetColumnPlan(true)).toEqual({
      keys: ['name', 'values', 'updatedAt'],
      nameEditable: false,
    })
  })

  it('adds the owner column after the name when listing all users', () => {
    expect(presetColumnPlan(false, true).keys).toEqual([
      'name',
      'owner',
      'values',
      'updatedAt',
      'delete',
    ])
    expect(presetColumnPlan(true, true).keys).toEqual(['name', 'owner', 'values', 'updatedAt'])
  })
})
