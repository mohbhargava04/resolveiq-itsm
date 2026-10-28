import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// In development the Vite server proxies /api to the Spring Boot backend, so the browser
// talks to one origin and no CORS configuration is needed.
const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
  },
})
