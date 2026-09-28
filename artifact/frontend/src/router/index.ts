import { createRouter, createWebHistory } from 'vue-router'

// History mode: the backend forwards unknown non-API paths to index.html (SpaWebConfig).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue') },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
    },
  ],
})
