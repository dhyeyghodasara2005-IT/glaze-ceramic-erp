import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// This file runs in Node. The project has no Node type package, so these lines describe
// the few Node things used below (`process.env` and the proxy's "proxyReq" event).
declare const process: { env: Record<string, string | undefined> }
interface ProxyEvents {
  on(event: 'proxyReq', listener: (proxyReq: { removeHeader(name: string): void }) => void): void
}

// During development the React app runs on :5173 and the Spring Boot API on :8080.
// The proxy forwards every /api/... call to Spring Boot, so the browser sees one website.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // true = also reachable from other devices on your Wi-Fi (test on your phone at http://<mac-ip>:5173)
    host: true,
    proxy: {
      '/api': {
        // API_PROXY lets a second copy run against another backend (e.g. a preview on :8081).
        target: process.env.API_PROXY ?? 'http://localhost:8080',
        // Through this proxy the browser only ever talks to ONE address, so an API call is not a
        // cross-site request. Drop the Origin header: otherwise Spring's CORS check refuses every
        // page address it doesn't list - like a phone on http://192.168.x.x:5173.
        configure: (proxy) => (proxy as unknown as ProxyEvents).on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
      },
    },
  },
})
