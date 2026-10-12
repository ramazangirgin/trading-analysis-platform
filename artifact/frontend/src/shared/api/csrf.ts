const COOKIE_NAME = 'XSRF-TOKEN'

/** The CSRF token the backend put in the `XSRF-TOKEN` cookie, or null when there is none. */
export function csrfToken(cookie: string = document.cookie): string | null {
  for (const part of cookie.split(';')) {
    const eq = part.indexOf('=')
    if (eq < 0 || part.slice(0, eq).trim() !== COOKIE_NAME) continue
    const value = part.slice(eq + 1).trim()
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }
  return null
}
