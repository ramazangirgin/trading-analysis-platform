import { createRouter, createWebHistory } from 'vue-router'
import { passwordGateRedirect, useSessionStore } from '@/features/auth'

// History mode: the backend forwards unknown non-API paths to index.html (SpaWebConfig).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
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
    { path: '/', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue') },
    { path: '/analyses', name: 'analyses', component: () => import('@/pages/RunsPage.vue') },
    {
      path: '/analyses/new',
      name: 'new-analysis',
      component: () => import('@/pages/NewAnalysisPage.vue'),
    },
    {
      path: '/analyses/compare',
      name: 'compare',
      component: () => import('@/pages/ComparePage.vue'),
      meta: { wide: true },
    },
    {
      path: '/analyses/:id',
      name: 'analysis',
      component: () => import('@/pages/RunDetailPage.vue'),
    },
    {
      path: '/reports',
      name: 'reports',
      component: () => import('@/pages/ReportsPage.vue'),
      meta: { wide: true },
    },
    {
      path: '/reports/:id',
      name: 'report',
      component: () => import('@/pages/ReportsPage.vue'),
      meta: { wide: true },
    },
    { path: '/settings', name: 'settings', component: () => import('@/pages/SettingsPage.vue') },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
    },
  ],
})

// Who is logged in is known before the first page. Nothing redirects on "no user" here: with login
// off there is never one. The login page comes from the 401 handler (main.ts). A user who must
// change the password sees nothing but the change-password page (and login).
let sessionLoaded = false
router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!sessionLoaded) {
    sessionLoaded = true
    try {
      await session.load()
    } catch {
      // Not a 401: the page shows its own error when it loads its data.
    }
  }
  return passwordGateRedirect(session.mustChangePassword, to) ?? true
})
