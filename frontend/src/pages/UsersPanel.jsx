import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { ROLE_LABEL } from '../auth/roles.js'

const EMPTY = { fullName: '', email: '', role: 'HR_MANAGER', departmentId: '', password: '' }

/** HR Admin only: create accounts and assign roles / departments (AAS-F-002). */
export default function UsersPanel() {
  const { user: me } = useAuth()
  const [users, setUsers] = useState([])
  const [departments, setDepartments] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api('/api/users').then(setUsers)
    api('/api/departments').then(setDepartments)
  }, [])
  useEffect(() => {
    load()
  }, [load])

  const deptName = (id) => departments.find((d) => d.id === id)?.name ?? '—'

  async function create(e) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/users', { method: 'POST', body: { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null } })
      setForm(EMPTY)
      load()
    } catch (err) {
      const f = err.body?.fieldErrors?.[0]
      setError(f ? `${f.field} ${f.message}` : err.message)
    }
  }

  async function toggle(u) {
    setError('')
    try {
      await api(`/api/users/${u.id}`, { method: 'PUT', body: { fullName: u.fullName, role: u.role, departmentId: u.departmentId, active: !u.active } })
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="card">
      <h2>Users</h2>
      <table className="table">
        <thead>
          <tr><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>One-time code</th><th /></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id} className={u.active ? '' : 'inactive'}>
              <td>{u.fullName}</td>
              <td>{u.email}</td>
              <td>{ROLE_LABEL[u.role]}</td>
              <td>{deptName(u.departmentId)}</td>
              <td>{u.role === 'HR_ADMIN' || u.role === 'HR_MANAGER' ? (u.mfaEnabled ? 'Set up' : 'Pending') : 'Not required'}</td>
              <td className="right">
                {u.id !== me.id && <button className="btn small" onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <form className="inline-form" onSubmit={create}>
        <input placeholder="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
        <input type="email" placeholder="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
          {Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })} required={form.role === 'DEPT_MANAGER'}>
          <option value="">Department…</option>
          {departments.filter((d) => d.active).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
        <input type="password" placeholder="Temporary password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} minLength={8} required />
        <button className="btn primary">Add user</button>
      </form>
      {error && <p className="error">{error}</p>}
    </div>
  )
}
