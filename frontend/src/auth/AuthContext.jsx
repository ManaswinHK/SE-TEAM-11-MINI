import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, loadSession, saveSession, setUnauthorizedHandler } from '../api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => {
    const s = loadSession()
    return s && s.expiresAt > Date.now() ? s : null
  })

  const startSession = useCallback((r) => {
    const s = { accessToken: r.accessToken, expiresAt: Date.now() + r.expiresIn * 1000, user: r.user }
    saveSession(s)
    setSession(s)
  }, [])

  const logout = useCallback(async () => {
    if (loadSession()) {
      try {
        await api('/api/auth/logout', { method: 'POST' })
      } catch {
        // token may already be expired; signing out locally is enough
      }
    }
    saveSession(null)
    setSession(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      saveSession(null)
      setSession(null)
    })
  }, [])

  const value = useMemo(
    () => ({ user: session?.user ?? null, startSession, logout, hasRole: (...roles) => roles.includes(session?.user?.role) }),
    [session, startSession, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
