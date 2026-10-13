import { describe, expect, it } from 'vitest'
import { weakPasswordMessages } from './passwordPolicy'

describe('weakPasswordMessages', () => {
  it('maps every reason to its key and params', () => {
    const params = {
      reasons: ['too_short', 'too_long', 'common', 'same_as_username'],
      minLength: 12,
      maxLength: 128,
    }
    expect(weakPasswordMessages(params)).toEqual([
      { key: 'password.policy.too_short', params: { minLength: 12 } },
      { key: 'password.policy.too_long', params: { maxLength: 128 } },
      { key: 'password.policy.common', params: {} },
      { key: 'password.policy.same_as_username', params: {} },
    ])
  })

  it('keeps the order of the reasons', () => {
    const keys = weakPasswordMessages({ reasons: ['common', 'too_short'], minLength: 8 }).map(
      (m) => m.key,
    )
    expect(keys).toEqual(['password.policy.common', 'password.policy.too_short'])
  })

  it('skips unknown reasons', () => {
    expect(weakPasswordMessages({ reasons: ['new_rule', 'common'] })).toEqual([
      { key: 'password.policy.common', params: {} },
    ])
  })

  it('falls back to the general message without a known reason', () => {
    const general = [{ key: 'errors.weak_password', params: {} }]
    expect(weakPasswordMessages({})).toEqual(general)
    expect(weakPasswordMessages({ reasons: ['new_rule'] })).toEqual(general)
    expect(weakPasswordMessages({ reasons: 'common' })).toEqual(general)
  })
})
