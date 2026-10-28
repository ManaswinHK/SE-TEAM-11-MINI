import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Login() {
  const { user, startSession } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function submitPassword(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const r = await api('/api/auth/login', { method: 'POST', body: { email, password }, anonymous: true })
      if (r.mfaRequired) setChallenge(r)
      else startSession(r)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function submitCode(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const r = await api('/api/auth/verify-otp', {
        method: 'POST',
        body: { challengeToken: challenge.challengeToken, code },
        anonymous: true,
      })
      startSession(r)
    } catch (err) {
      setError(err.message)
      if (err.body?.code === 'CHALLENGE_EXPIRED' || err.body?.code === 'ACCOUNT_LOCKED') setChallenge(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand login-brand">
          <span className="brand-mark">A</span>
          <span>Attrition Analysis System</span>
        </div>

        {!challenge ? (
          <form onSubmit={submitPassword}>
            <h1>Sign in</h1>
            <label>
              Email
              <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </label>
            <label>
              Password
              <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="btn primary block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
          </form>
        ) : (
          <form onSubmit={submitCode}>
            <h1>Verify it&apos;s you</h1>
            {challenge.enrollment ? (
              <div className="callout">
                <p><strong>Set up your authenticator app</strong> (Google or Microsoft Authenticator):</p>
                <p>Add an account manually with this key:</p>
                <code className="secret">{challenge.enrollment.secret}</code>
              </div>
            ) : (
              <p className="muted">Enter the 6-digit code from your authenticator app.</p>
            )}
            {challenge.devOtp && (
              <p className="muted small">Development mode, current code: <strong>{challenge.devOtp}</strong></p>
            )}
            <label>
              One-time code
              <input inputMode="numeric" pattern="\d{6}" maxLength={6} autoComplete="one-time-code" value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} required autoFocus />
            </label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="btn primary block" disabled={busy || code.length !== 6}>{busy ? 'Verifying…' : 'Verify'}</button>
            <button type="button" className="btn link block" onClick={() => { setChallenge(null); setCode(''); setError('') }}>
              Back
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
