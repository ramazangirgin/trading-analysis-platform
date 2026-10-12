/** The page to return to after login: a path of this app only, else the start page. */
export function safeRedirect(value: unknown): string {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/'
  const path = value.split(/[?#]/)[0]
  return path === '/login' ? '/' : value
}
