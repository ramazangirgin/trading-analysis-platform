import pluginVue from 'eslint-plugin-vue'
import boundaries from 'eslint-plugin-boundaries'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

// Folder structure: docs/coding-convention/frontend-folder-structure.md.
// app -> pages -> features -> shared; nothing imports upwards. Imports inside one element (one
// feature, one shared segment) are not checked; a feature is entered through its index.ts only.
const anyFileOf = (...types) => ({ to: { element: { types: { anyOf: types } } } })
const featureIndex = { to: { element: { type: 'feature', fileInternalPath: 'index.ts' } } }
const allowedDependencies = {
  app: [anyFileOf('pages', 'shared'), featureIndex],
  pages: [anyFileOf('shared'), featureIndex],
  feature: [anyFileOf('shared'), featureIndex],
  shared: [anyFileOf('shared')],
}

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/coverage/**', '**/.gradle/**'],
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  {
    name: 'app/folder-structure',
    files: ['src/**/*.{ts,vue}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { alwaysTryTypes: true } },
      'boundaries/include': ['src/**/*'],
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'pages', pattern: 'src/pages' },
        { type: 'feature', pattern: 'src/features/*', capture: ['feature'] },
        { type: 'shared', pattern: 'src/shared/*', capture: ['segment'] },
      ],
    },
    rules: {
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          message: 'This import breaks the folder structure (frontend-folder-structure.md).',
          policies: Object.entries(allowedDependencies).flatMap(([from, targets]) =>
            targets.map((allow) => ({ from: { element: { type: from } }, allow })),
          ),
        },
      ],
      'boundaries/no-unknown-files': 'error',
      // Only a feature's api.ts calls the backend.
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/shared/api/client', '**/shared/api/client', '**/api/client'],
              message: "Call the backend from a feature's api.ts (frontend-folder-structure.md).",
            },
            {
              group: ['@/shared/api/schema', '**/shared/api/schema', '**/api/schema'],
              message: 'Use the types in @/shared/api/types.',
            },
          ],
        },
      ],
    },
  },
  {
    name: 'app/api-modules',
    files: ['src/features/*/api.ts', 'src/shared/api/**'],
    rules: { 'no-restricted-imports': 'off' },
  },
  skipFormatting,
)
