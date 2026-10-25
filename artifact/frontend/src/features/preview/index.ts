// Public API of the preview feature: what a visitor sees of partially public analyses.
// Pages and other features import from here only (docs/coding-convention/frontend-folder-structure.md).
export { previewApi } from './api'
export { default as LockedSection } from './components/LockedSection.vue'
export { default as PreviewCard } from './components/PreviewCard.vue'
export { default as SignUpCallToAction } from './components/SignUpCallToAction.vue'
export { lockedTitleKey } from './model/lockedTitles'
export { loginLocation } from './model/loginTarget'
