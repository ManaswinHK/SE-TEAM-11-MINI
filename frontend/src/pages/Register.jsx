import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'

export default function Register() {
  const [form, setForm] = useState({ organizationName: '', industry: '', adminName: '', adminEmail: '', password: '' })
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/organizations/register', { method: 'POST', body: form, anonymous: true })
      setDone(true)
    } catch (err) {
      const f = err.body?.fieldErrors?.[0]
      setError(f ? `${f.field}: ${f.message}` : err.message)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand login-brand">
          <span className="brand-mark">A</span>
          <span>Attrition Analysis System</span>
        </div>
        {done ? (
          <>
            <h1>Organization created</h1>
            <p>Sign in as <strong>{form.adminEmail}</strong>. You will be asked to set up a one-time code app.</p>
            <Link className="btn primary block center" to="/login">Go to sign in</Link>
          </>
        ) : (
          <form onSubmit={submit}>
            <h1>Register your organization</h1>
            <label>Organization name<input value={form.organizationName} onChange={set('organizationName')} required /></label>
            <label>Industry<input value={form.industry} onChange={set('industry')} /></label>
            <label>Your name<input value={form.adminName} onChange={set('adminName')} required /></label>
            <label>Work email<input type="email" value={form.adminEmail} onChange={set('adminEmail')} required /></label>
            <label>Password<input type="password" value={form.password} onChange={set('password')} minLength={8} required /></label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="btn primary block">Create organization</button>
            <Link className="btn link block center" to="/login">Back to sign in</Link>
          </form>
        )}
      </div>
    </div>
  )
}
