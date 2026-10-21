import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider } from 'antd'
import { MotionConfig } from 'motion/react'
import enUS from 'antd/locale/en_US'
// Fonts are bundled with the app (not loaded from Google), so they work offline on the factory floor.
import '@fontsource-variable/plus-jakarta-sans'
import '@fontsource-variable/noto-sans-gujarati'
import './i18n'
import './styles.css'
import { theme } from './theme'
import { AuthProvider } from './auth/AuthContext'
import App from './App'

// Caches server data. "staleTime" = how long fetched data counts as fresh before refetching.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
})

// Providers wrap the app so every component can use: routing, server cache, theme, animation, login state.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        {/* antd has no Gujarati locale yet, so its built-in texts (pagination etc.) stay English */}
        <ConfigProvider theme={theme} locale={enUS}>
          {/* reducedMotion="user": people who turn off animations in their phone/OS settings get none */}
          <MotionConfig reducedMotion="user">
            <AntApp>
              <AuthProvider>
                <App />
              </AuthProvider>
            </AntApp>
          </MotionConfig>
        </ConfigProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)
