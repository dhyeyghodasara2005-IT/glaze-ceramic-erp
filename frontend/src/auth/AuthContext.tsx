import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, plantStore, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { CurrentUser, Language, LoginResponse, Permission, PlantRef } from '../api/types'
import i18n from '../i18n'

/**
 * Holds "who is logged in" for the whole app.
 * Any component can call useAuth() to read the user, check permissions, or log out.
 */
interface AuthContextValue {
  user: CurrentUser | null
  /** true while we check a saved token on page load */
  loading: boolean
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => void
  /** Re-reads the user from the server (after a password change, etc.) */
  refresh: () => Promise<void>
  /** can('USER_MANAGE') -> does the user hold this permission? */
  can: (permission: Permission) => boolean
  setLanguage: (language: Language) => Promise<void>
  /** The plant the user is currently working in (chosen in the header). */
  currentPlant: PlantRef | null
  setCurrentPlantId: (id: number) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)
  const [plantId, setPlantId] = useState<number | null>(() => Number(plantStore.get()) || null)

  const applyUser = useCallback((u: CurrentUser) => {
    setUser(u)
    if (u.preferredLanguage && u.preferredLanguage !== i18n.language) {
      i18n.changeLanguage(u.preferredLanguage)
    }
  }, [])

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
    queryClient.clear() // forget cached data from the previous user
  }, [queryClient])

  const refresh = useCallback(async () => {
    const { data } = await api.get<CurrentUser>('/auth/me')
    applyUser(data)
  }, [applyUser])

  // On first load: if a token was saved, check it is still valid.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) return
    refresh()
      .catch(() => logout())
      .finally(() => setLoading(false))
  }, [logout, refresh])

  const login = useCallback(
    async (username: string, password: string) => {
      const { data } = await api.post<LoginResponse>('/auth/login', { username, password })
      tokenStore.set(data.token)
      applyUser(data.user)
      return data.user
    },
    [applyUser],
  )

  const setLanguage = useCallback(
    async (language: Language) => {
      await i18n.changeLanguage(language)
      if (!user) return
      try {
        const { data } = await api.patch<CurrentUser>('/auth/me/preferences', { preferredLanguage: language })
        setUser(data)
      } catch {
        // Not critical: the screen already switched; it just won't be remembered on the server.
      }
    },
    [user],
  )

  const currentPlant = useMemo(() => {
    if (!user || user.plants.length === 0) return null
    return user.plants.find((p) => p.id === plantId) ?? user.plants[0]
  }, [user, plantId])

  const setCurrentPlantId = useCallback((id: number) => {
    plantStore.set(String(id))
    setPlantId(id)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      login,
      logout,
      refresh,
      can: (p) => user?.permissions.includes(p) ?? false,
      setLanguage,
      currentPlant,
      setCurrentPlantId,
    }),
    [user, loading, login, logout, refresh, setLanguage, currentPlant, setCurrentPlantId],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
