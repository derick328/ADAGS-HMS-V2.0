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
    },
  },
})
