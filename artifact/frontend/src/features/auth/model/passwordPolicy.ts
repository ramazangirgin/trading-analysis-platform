export interface PolicyMessage {
  key: string
  params: Record<string, unknown>
}

/**
 * The messages for a `weak_password` answer, one per known reason in the order the backend gave
 * them. Unknown reasons are skipped; without a known one the general message stays, so the field
 * always shows something.
 */
export function weakPasswordMessages(params: Record<string, unknown>): PolicyMessage[] {
  const reasons = Array.isArray(params.reasons) ? params.reasons : []
  const messages: PolicyMessage[] = []
  for (const reason of reasons) {
    switch (reason) {
      case 'too_short':
        messages.push({ key: 'password.policy.too_short', params: { minLength: params.minLength } })
        break
      case 'too_long':
        messages.push({ key: 'password.policy.too_long', params: { maxLength: params.maxLength } })
        break
      case 'common':
        messages.push({ key: 'password.policy.common', params: {} })
        break
      case 'same_as_username':
        messages.push({ key: 'password.policy.same_as_username', params: {} })
        break
    }
  }
  return messages.length > 0 ? messages : [{ key: 'errors.weak_password', params: {} }]
}
