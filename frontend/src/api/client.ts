import axios from 'axios'

// Small wrapper around localStorage. Reads/writes can fail (private mode, blocked storage),
// so every access is in try/catch and the app keeps working without it.
function storage(key: string) {
  return {
    get(): string | null {
      try {
        return localStorage.getItem(key)
      } catch {
        return null
      }
    },
    set(value: string | null) {
      try {
        if (value === null) localStorage.removeItem(key)
        else localStorage.setItem(key, value)
      } catch {
        /* ignore */
      }
    },
  }
}

export const tokenStore = storage('erp.token')
export const plantStore = storage('erp.plantId')
export const languageStore = storage('erp.language')
export const sidenavStore = storage('erp.sidenav')

/**
 * The one HTTP client the whole app uses.
 * baseURL '/api' -> in development Vite forwards it to Spring Boot on :8080 (see vite.config.ts).
 */
export const api = axios.create({ baseURL: '/api' })

// Before every request: attach the login token.
api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// After every response: if the server says 401 (token expired / user deactivated), log out.
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginCall = error.config?.url?.endsWith('/auth/login')
    if (error.response?.status === 401 && !isLoginCall) onUnauthorized()
    return Promise.reject(error)
  },
)
