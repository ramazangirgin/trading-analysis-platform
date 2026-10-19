// Public API of the auth feature: the current user and the login, password and logout UI.
// Pages and other features import from here only (docs/coding-convention/frontend-folder-structure.md).
export { onPasswordChangeRequired, onSessionEnded } from './api'
export { useSessionStore } from './store'
export { safeRedirect } from './model/redirect'
export { weakPasswordMessages } from './model/passwordPolicy'
export { passwordGateRedirect } from './model/passwordGate'
export { PERMISSIONS, permissionGateRedirect } from './model/permissions'
export { default as LoginForm } from './components/LoginForm.vue'
export { default as ChangePasswordForm } from './components/ChangePasswordForm.vue'
export { default as UserMenu } from './components/UserMenu.vue'
