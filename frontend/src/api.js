// Thin wrapper around fetch for the AAS REST API (/api is proxied to Spring Boot in dev).

const TOKEN_KEY = 'aas.session'
const REFRESH_BEFORE_MS = 10 * 60 * 1000

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`)
    this.status = status
    this.body = body
  }
}

export function loadSession() {
  try {
    return JSON.parse(sessionStorage.getItem(TOKEN_KEY))
  } catch {
    return null
  }
}

export function saveSession(session) {
  try {
    if (session) sessionStorage.setItem(TOKEN_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage unavailable (private mode): the session simply lasts for this page only
  }
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

async function raw(path, { method = 'GET', body, token } = {}) {
  const isForm = body instanceof FormData
  const res = await fetch(path, {
    method,
    headers: {
      ...(body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined || isForm ? body : JSON.stringify(body),
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = { message: text }
  }
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

// Sliding session (AAS-SR-008): activity within the last 10 minutes of a token renews it,
// so a user who is idle for 30 minutes is signed out.
async function freshToken() {
  const session = loadSession()
  if (!session) return null
  if (session.expiresAt - Date.now() < REFRESH_BEFORE_MS && session.expiresAt > Date.now()) {
    const r = await raw('/api/auth/refresh', { method: 'POST', token: session.accessToken })
    const next = { ...session, accessToken: r.accessToken, expiresAt: Date.now() + r.expiresIn * 1000, user: r.user }
    saveSession(next)
    return next.accessToken
  }
  return session.accessToken
}

export async function api(path, options = {}) {
  try {
    const token = options.anonymous ? null : await freshToken()
    return await raw(path, { ...options, token })
  } catch (e) {
    if (e instanceof ApiError && e.status === 401 && !options.anonymous) onUnauthorized()
    throw e
  }
}
