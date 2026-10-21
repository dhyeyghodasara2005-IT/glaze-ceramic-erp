import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// During development the React app runs on :5173 and the Spring Boot API on :8080.
// The proxy forwards every /api/... call to Spring Boot, so the browser sees one website.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // true = also reachable from other devices on your Wi-Fi (test on your phone at http://<mac-ip>:5173)
    host: true,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
