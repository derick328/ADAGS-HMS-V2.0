/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxied so the browser sees one origin in dev, matching how the reverse
// proxy will front both in production — avoids CORS and keeps the
// SameSite=Strict refresh cookie (see backend RefreshCookieFactory) simple.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/me': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
  // Vitest runs the pure logic the money screens depend on — axis scaling,
  // date-range resolution, category labelling, currency formatting. jsdom is
  // only needed for the component-level render checks; the rest is plain
  // module testing and runs without a DOM.
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
