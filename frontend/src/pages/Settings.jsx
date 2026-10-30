import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'
import HeadcountPanel from './HeadcountPanel.jsx'
import UsersPanel from './UsersPanel.jsx'

const CATEGORY_LABEL = { VOLUNTARY: 'Voluntary', INVOLUNTARY: 'Involuntary', OTHER: 'Other' }

function OrganizationForm({ canEdit }) {
  const [org, setOrg] = useState(null)
  const [msg, setMsg] = useState(null)

  useEffect(() => {
    api('/api/org').then(setOrg)
  }, [])

  async function save(e) {
    e.preventDefault()
    setMsg(null)
    try {
      setOrg(await api('/api/org', { method: 'PUT', body: { ...org, riskThreshold: Number(org.riskThreshold) } }))
      setMsg({ ok: true, text: 'Saved' })
    } catch (err) {
      setMsg({ ok: false, text: err.body?.fieldErrors?.[0] ? `${err.body.fieldErrors[0].field} ${err.body.fieldErrors[0].message}` : err.message })
    }
  }

  if (!org) return <p className="muted">Loading…</p>
  return (
    <form className="card" onSubmit={save}>
      <h2>Organization</h2>
      <div className="grid-3">
        <label>Name<input value={org.name} disabled={!canEdit} onChange={(e) => setOrg({ ...org, name: e.target.value })} required /></label>
        <label>Industry<input value={org.industry || ''} disabled={!canEdit} onChange={(e) => setOrg({ ...org, industry: e.target.value })} /></label>
        <label>
          Attrition alert threshold (%)
          <input type="number" step="0.1" min="0.1" max="100" value={org.riskThreshold} disabled={!canEdit}
            onChange={(e) => setOrg({ ...org, riskThreshold: e.target.value })} required />
        </label>
      </div>
      {msg && <p className={msg.ok ? 'success' : 'error'}>{msg.text}</p>}
      {canEdit && <button className="btn primary">Save</button>}
    </form>
  )
}

function ListEditor({ title, path, canEdit, columns, emptyForm, renderFields, toBody }) {
  const [items, setItems] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')

  const load = useCallback(() => api(path).then(setItems), [path])
  useEffect(() => {
    load()
  }, [load])

  async function add(e) {
    e.preventDefault()
    setError('')
    try {
      await api(path, { method: 'POST', body: toBody(form) })
      setForm(emptyForm)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function toggle(item) {
    await api(`${path}/${item.id}`, { method: 'PUT', body: { ...toBody(item), active: !item.active } })
    load()
  }

  return (
    <div className="card">
      <h2>{title}</h2>
      <table className="table">
        <thead>
          <tr>
            {columns.map((c) => <th key={c.key}>{c.label}</th>)}
            <th>Status</th>
            {canEdit && <th />}
          </tr>
        </thead>
        <tbody>
          {items.map((it) => (
            <tr key={it.id} className={it.active ? '' : 'inactive'}>
              {columns.map((c) => <td key={c.key}>{c.render ? c.render(it) : it[c.key]}</td>)}
              <td>{it.active ? 'Active' : 'Inactive'}</td>
              {canEdit && (
                <td className="right">
                  <button className="btn small" onClick={() => toggle(it)}>{it.active ? 'Deactivate' : 'Activate'}</button>
                </td>
              )}
            </tr>
          ))}
          {items.length === 0 && (
            <tr><td colSpan={columns.length + 2} className="muted">None yet</td></tr>
          )}
        </tbody>
      </table>
      {canEdit && (
        <form className="inline-form" onSubmit={add}>
          {renderFields(form, setForm)}
          <button className="btn primary">Add</button>
        </form>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  )
}

export default function Settings() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('HR_ADMIN')

  return (
    <section>
      <h1>Settings</h1>
      {!canEdit && <p className="muted">Only HR Admins can change these settings.</p>}
      <OrganizationForm canEdit={canEdit} />
      <div className="grid-2">
        <ListEditor
          title="Departments"
          path="/api/departments"
          canEdit={canEdit}
          columns={[{ key: 'name', label: 'Name' }, { key: 'location', label: 'Location' }]}
          emptyForm={{ name: '', location: '' }}
          toBody={(f) => ({ name: f.name, location: f.location })}
          renderFields={(f, set) => (
            <>
              <input placeholder="Department name" value={f.name} onChange={(e) => set({ ...f, name: e.target.value })} required />
              <input placeholder="Location" value={f.location} onChange={(e) => set({ ...f, location: e.target.value })} />
            </>
          )}
        />
        <ListEditor
          title="Exit reasons"
          path="/api/exit-reasons"
          canEdit={canEdit}
          columns={[{ key: 'name', label: 'Reason' }, { key: 'category', label: 'Category', render: (r) => CATEGORY_LABEL[r.category] }]}
          emptyForm={{ name: '', category: 'VOLUNTARY' }}
          toBody={(f) => ({ name: f.name, category: f.category })}
          renderFields={(f, set) => (
            <>
              <input placeholder="Reason" value={f.name} onChange={(e) => set({ ...f, name: e.target.value })} required />
              <select value={f.category} onChange={(e) => set({ ...f, category: e.target.value })}>
                {Object.entries(CATEGORY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </>
          )}
        />
      </div>
      {canEdit && <UsersPanel />}
      {hasRole('HR_ADMIN', 'HR_MANAGER', 'LEADERSHIP') && <HeadcountPanel />}
    </section>
  )
}
