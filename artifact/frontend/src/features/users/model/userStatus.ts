import type { User } from '@/shared/api/types'

export type UserStatus = 'disabled' | 'locked' | 'active'

export function userStatus(user: Pick<User, 'enabled' | 'locked'>): UserStatus {
  if (!user.enabled) return 'disabled'
  return user.locked ? 'locked' : 'active'
}
