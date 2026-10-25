import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import {
  PERMISSIONS,
  passwordGateRedirect,
  permissionGateRedirect,
  useSessionStore,
} from '@/features/auth'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { bare: true },
  },
  {
    path: '/password',
    name: 'change-password',
    component: () => import('@/pages/ChangePasswordPage.vue'),
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/pages/AboutPage.vue'),
    meta: { public: true },
  },
  {
    path: '/preview/:id',
    name: 'preview',
    component: () => import('@/pages/PreviewPage.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    name: 'dashboard',
    component: () => import('@/pages/HomePage.vue'),
    meta: { visitors: true },
  },
  {
    path: '/analyses',
    name: 'analyses',
    component: () => import('@/pages/RunsPage.vue'),
    meta: { permission: PERMISSIONS.analysisRead },
  },
  {
    path: '/analyses/new',
    name: 'new-analysis',
    component: () => import('@/pages/NewAnalysisPage.vue'),
    meta: { permission: PERMISSIONS.analysisRun },
  },
  {
    path: '/analyses/compare',
    name: 'compare',
    component: () => import('@/pages/ComparePage.vue'),
    meta: { wide: true, permission: PERMISSIONS.analysisRead },
  },
  {
    path: '/analyses/:id',
    name: 'analysis',
    component: () => import('@/pages/RunDetailPage.vue'),
    meta: { permission: PERMISSIONS.analysisRead },
  },
  {
    path: '/reports',
    name: 'reports',
    component: () => import('@/pages/ReportsPage.vue'),
    meta: { wide: true, permission: PERMISSIONS.analysisRead },
  },
  {
    path: '/reports/:id',
    name: 'report',
    component: () => import('@/pages/ReportsPage.vue'),
    meta: { wide: true, permission: PERMISSIONS.analysisRead },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { permission: PERMISSIONS.settingsRead },
  },
  {
    path: '/admin/users',
    name: 'users',
    component: () => import('@/pages/UsersPage.vue'),
    meta: { permission: PERMISSIONS.userRead },
  },
  {
    path: '/admin/roles',
    name: 'roles',
    component: () => import('@/pages/RolesPage.vue'),
    meta: { permission: PERMISSIONS.roleManage },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/pages/NotFoundPage.vue'),
  },
]

type GuardTarget = Pick<RouteLocationNormalized, 'name' | 'fullPath' | 'meta'>

// Who is logged in is known before the first page. A navigation without a session goes to the login
// page here (the permission gate would otherwise send it to the dashboard and lose the wanted
// page); the 401 handler in main.ts covers a session that ends while a page is open. A user who
// must change the password sees nothing but the change-password page (and login). A `visitors`
// route is open without a session only: with one it goes through the gates like any other.
export function createSessionGuard() {
  let sessionLoaded = false
  return async (to: GuardTarget) => {
    const session = useSessionStore()
    if (!sessionLoaded) {
      sessionLoaded = true
      try {
        await session.load()
      } catch {
        // Not a 401: the page shows its own error when it loads its data.
      }
    }
    if (to.meta.public) return true
    if (to.meta.visitors && !session.user) return true
    // No session (the 401 handler in main.ts only knows the route before this navigation): to the
    // login page, which returns to the wanted page afterwards.
    if (session.loaded && !session.user && to.name !== 'login') {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    return (
      passwordGateRedirect(session.mustChangePassword, to) ??
      permissionGateRedirect(session.can, to) ??
      true
    )
  }
}
