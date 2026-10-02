import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// In production the backend serves dist/ from its jar on the same origin.
// In dev, Vite serves the UI and proxies the backend paths to `./gradlew :backend:bootRun`.
const backend = 'http://localhost:8080'

// The platform version (scripts/version.sh keeps package.json in step with gradle.properties).
const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

export default defineConfig({
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': backend,
      '/actuator': backend,
    },
  },
  test: {
    environment: 'node',
  },
})
