import { createRouter, createWebHistory } from 'vue-router'
import { createSessionGuard, routes } from './routes'

// History mode: the backend forwards unknown non-API paths to index.html (SpaWebConfig).
export const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(createSessionGuard())
