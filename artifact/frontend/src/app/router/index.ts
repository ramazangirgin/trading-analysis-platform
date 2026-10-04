import { createRouter, createWebHistory } from 'vue-router'

// History mode: the backend forwards unknown non-API paths to index.html (SpaWebConfig).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
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
