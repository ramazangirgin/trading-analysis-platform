import type { RouteLocationRaw } from 'vue-router'

/** The login route that returns to the full analysis afterwards. */
export function loginLocation(analysisId: string): RouteLocationRaw {
  return { name: 'login', query: { redirect: `/analyses/${encodeURIComponent(analysisId)}` } }
}
