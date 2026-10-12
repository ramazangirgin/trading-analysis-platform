import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { onPasswordChangeRequired, onSessionEnded, useSessionStore } from '@/features/auth'
import { i18n } from '@/shared/i18n'
import { router } from './router'

const app = createApp(App).use(createPinia()).use(router).use(i18n)

// An ended session (any 401 but login's own): back to the login page, which returns here afterwards.
onSessionEnded(() => {
  useSessionStore().clear()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

// A request refused until the password is changed (the stored flag was not known yet): to the page.
onPasswordChangeRequired(() => {
  useSessionStore().requirePasswordChange()
  const current = router.currentRoute.value
  if (current.name === 'change-password') return
  void router.replace({ name: 'change-password', query: { redirect: current.fullPath } })
})

app.mount('#app')
