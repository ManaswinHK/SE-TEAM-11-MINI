import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { SEPARATION_STATUS, SEPARATION_TYPE } from '../labels.js'

const today = () => new Date().toISOString().slice(0, 10)

function RecordForm({ reasons, onCancel, onSaved }) {
  const [query, setQuery] = useState('')
  const [matches, setMatches] = useState([])
  const [employee, setEmployee] = useState(null)
  const [form, setForm] = useState({ resignationDate: today(), lastWorkingDay: '', type: 'VOLUNTARY', reasonId: '', regretted: false, remarks: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const searching = !employee && query.trim().length >= 2
  useEffect(() => {
    if (!searching) return undefined
    const t = setTimeout(() => {
      api(`/api/employees?status=ACTIVE&size=8&q=${encodeURIComponent(query.trim())}`).then((r) => setMatches(r.content))
    }, 250)
    return () => clearTimeout(t)
  }, [query, searching])

  // Show reasons that fit the separation type (voluntary reasons for resignations, and so on).
  const category = { VOLUNTARY: 'VOLUNTARY', INVOLUNTARY: 'INVOLUNTARY' }[form.type] ?? 'OTHER'
  const reasonOptions = reasons.filter((r) => r.active && r.category === category)

  async function submit(e) {
    e.preventDefault()
    setErrors({})
    setError('')
    try {
      const saved = await api('/api/separations', {
        method: 'POST',
        body: { ...form, employeeId: employee.id, reasonId: Number(form.reasonId) },
      })
      onSaved(saved)
    } catch (err) {
      const fields = Object.fromEntries((err.body?.fieldErrors ?? []).map((f) => [f.field, f.message]))
      setErrors(fields)
      if (!Object.keys(fields).length) setError(err.message)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <form className="modal" onSubmit={submit}>
        <h2>Record separation</h2>
        {!employee ? (
          <label>
            Employee
            <input placeholder="Type a name or employee ID" value={query} onChange={(e) => setQuery(e.target.value)} autoFocus required />
            {searching && matches.length > 0 && (
              <ul className="suggestions">
                {matches.map((m) => (
                  <li key={m.id}>
                    <button type="button" onClick={() => setEmployee(m)}>
                      {m.fullName} <span className="muted">· {m.employeeCode} · {m.departmentName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </label>
        ) : (
          <div className="callout selected-employee">
            <strong>{employee.fullName}</strong> · {employee.employeeCode} · {employee.departmentName} · joined {employee.joinDate}
            <button type="button" className="btn link" onClick={() => { setEmployee(null); setQuery('') }}>Change</button>
          </div>
        )}
        <div className="grid-3">
          <label>
            Resignation date
            <input type="date" value={form.resignationDate} max={today()} onChange={set('resignationDate')} required />
            {errors.resignationDate && <span className="field-error">{errors.resignationDate}</span>}
          </label>
          <label>
            Last working day
            <input type="date" value={form.lastWorkingDay} onChange={set('lastWorkingDay')} required />
            {errors.lastWorkingDay && <span className="field-error">{errors.lastWorkingDay}</span>}
          </label>
          <label>
            Type
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value, reasonId: '' })}>
              {Object.entries(SEPARATION_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label>
            Primary reason
            <select value={form.reasonId} onChange={set('reasonId')} required>
              <option value="">Select…</option>
              {reasonOptions.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
            </select>
            {errors.reasonId && <span className="field-error">{errors.reasonId}</span>}
          </label>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={form.regretted} onChange={set('regretted')} />
          Regretted loss (we wanted to keep this person)
        </label>
        <label>
          Remarks
          <textarea rows={2} maxLength={1000} value={form.remarks} onChange={set('remarks')} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onCancel}>Cancel</button>
          <button className="btn primary" disabled={!employee}>Save</button>
        </div>
      </form>
    </div>
  )
}

export default function Separations() {
  const { hasRole } = useAuth()
  const canRecord = hasRole('HR_ADMIN', 'HR_MANAGER')
  const [filters, setFilters] = useState(() => {
    const year = new Date().getFullYear()
    return { from: `${year}-01-01`, to: `${year}-12-31`, departmentId: '', type: '' }
  })
  const [departments, setDepartments] = useState([])
  const [reasons, setReasons] = useState([])
  const [rows, setRows] = useState(null)
  const [recording, setRecording] = useState(false)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    api('/api/departments').then(setDepartments)
    api('/api/exit-reasons').then(setReasons)
  }, [])

  const load = useCallback(() => {
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([k, v]) => v && params.set(k, v))
    api(`/api/separations?${params}`).then(setRows)
  }, [filters])

  useEffect(() => {
    load()
  }, [load])

  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value })

  return (
    <section>
      <div className="page-head">
        <h1>Separations</h1>
        {canRecord && <button className="btn primary" onClick={() => setRecording(true)}>Record separation</button>}
      </div>
      {notice && <p className="success">{notice}</p>}
      <div className="card">
        <div className="filters">
          <label className="inline">From<input type="date" value={filters.from} onChange={set('from')} /></label>
          <label className="inline">To<input type="date" value={filters.to} onChange={set('to')} /></label>
          <select value={filters.departmentId} onChange={set('departmentId')} aria-label="Department">
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
          <select value={filters.type} onChange={set('type')} aria-label="Type">
            <option value="">All types</option>
            {Object.entries(SEPARATION_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Employee</th><th>Department</th><th>Resigned</th><th>Last day</th><th>Tenure</th><th>Type</th><th>Reason</th><th>Regretted</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows?.map((s) => (
                <tr key={s.id}>
                  <td>{s.employeeName}<div className="muted small">{s.employeeCode}</div></td>
                  <td>{s.departmentName}</td>
                  <td>{s.resignationDate}</td>
                  <td>{s.lastWorkingDay}</td>
                  <td>{Math.floor(s.tenureMonths / 12)}y {s.tenureMonths % 12}m</td>
                  <td>{SEPARATION_TYPE[s.type]}</td>
                  <td>{s.reasonName}</td>
                  <td>{s.regretted ? 'Yes' : 'No'}</td>
                  <td><span className={s.status === 'COMPLETED' ? 'badge danger' : 'badge warn'}>{SEPARATION_STATUS[s.status]}</span></td>
                </tr>
              ))}
              {rows && rows.length === 0 && <tr><td colSpan={9} className="muted">No separations in this period.</td></tr>}
            </tbody>
          </table>
        </div>
        {rows && <p className="muted small">{rows.length} separations</p>}
      </div>
      {recording && (
        <RecordForm
          reasons={reasons}
          onCancel={() => setRecording(false)}
          onSaved={(s) => {
            setRecording(false)
            setNotice(`Separation recorded for ${s.employeeName} (last working day ${s.lastWorkingDay}).`)
            load()
          }}
        />
      )}
    </section>
  )
}
