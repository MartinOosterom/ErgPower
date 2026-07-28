import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The app consumes only /api/v1 (REST + SSE). In dev, proxy it to a running `serve`
// (java -jar ErgPower.jar serve) so the browser and API share an origin — SSE included.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
