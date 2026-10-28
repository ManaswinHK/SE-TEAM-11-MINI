import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { STATUS_CLASS, STATUS_LABEL } from '../labels.js'
import ImportDialog from './ImportDialog.jsx'

const EMPTY = {
  employeeCode: '', fullName: '', email: '', departmentId: '', designation: '', managerName: '', location: '',
  grade: '', gender: '', joinDate: '', performanceRating: '', lastPromotionDate: '',
}

function toBody(f) {
  return {
    ...f,
    departmentId: Number(f.departmentId),
    performanceRating: f.performanceRating ? Number(f.performanceRating) : null,
    lastPromotionDate: f.lastPromotionDate || null,
  }
}

function errorText(err) {
  const f = err.body?.fieldErrors?.[0]
  return f ? `${f.field}: ${f.message}` : err.message
}

function EmployeeForm({ initial, departments, onCancel, onSaved }) {
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      const saved = initial.id
        ? await api(`/api/employees/${initial.id}`, { method: 'PUT', body: toBody(form) })
        : await api('/api/employees', { method: 'POST', body: toBody(form) })
      onSaved(saved)
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <form className="modal" onSubmit={submit}>
        <h2>{initial.id ? 'Edit employee' : 'Add employee'}</h2>
        <div className="grid-3">
          <label>Employee ID<input value={form.employeeCode} onChange={set('employeeCode')} required /></label>
          <label>Full name<input value={form.fullName} onChange={set('fullName')} required /></label>
          <label>Email<input type="email" value={form.email ?? ''} onChange={set('email')} /></label>
          <label>
            Department
            <select value={form.departmentId} onChange={set('departmentId')} required>
              <option value="">Select…</option>
              {departments.filter((d) => d.active || d.id === Number(form.departmentId)).map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </label>
          <label>Designation<input value={form.designation ?? ''} onChange={set('designation')} /></label>
          <label>Manager<input value={form.managerName ?? ''} onChange={set('managerName')} /></label>
          <label>Location<input value={form.location ?? ''} onChange={set('location')} /></label>
          <label>Grade<input value={form.grade ?? ''} onChange={set('grade')} /></label>
          <label>
            Gender
            <select value={form.gender ?? ''} onChange={set('gender')}>
              <option value="">Prefer not to say</option>
              <option>Female</option>
              <option>Male</option>
              <option>Other</option>
            </select>
          </label>
          <label>Join date<input type="date" value={form.joinDate} onChange={set('joinDate')} required /></label>
          <label>
            Performance rating
            <select value={form.performanceRating ?? ''} onChange={set('performanceRating')}>
              <option value="">Not rated</option>
              {[1, 2, 3, 4, 5].map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </label>
          <label>Last promotion<input type="date" value={form.lastPromotionDate ?? ''} onChange={set('lastPromotionDate')} /></label>
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onCancel}>Cancel</button>
          <button className="btn primary">Save</button>
        </div>
      </form>
    </div>
  )
}

export default function Employees() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('HR_ADMIN', 'HR_MANAGER')
  const [departments, setDepartments] = useState([])
  const [filters, setFilters] = useState({ q: '', departmentId: '', status: 'ACTIVE' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [editing, setEditing] = useState(null)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/api/departments').then(setDepartments)
  }, [])

  const load = useCallback(() => {
    const params = new URLSearchParams({ page, size: 25 })
    Object.entries(filters).forEach(([k, v]) => v && params.set(k, v))
    api(`/api/employees?${params}`).then(setData).catch((err) => setError(err.message))
  }, [filters, page])

  useEffect(() => {
    load()
  }, [load])

  const updateFilter = (k) => (e) => {
    setPage(0)
    setFilters({ ...filters, [k]: e.target.value })
  }

  async function deactivate(emp) {
    if (!window.confirm(`Deactivate ${emp.fullName}? Use this only for records created by mistake. Record real exits under Separations.`)) return
    await api(`/api/employees/${emp.id}/deactivate`, { method: 'POST' })
    load()
  }

  return (
    <section>
      <div className="page-head">
        <h1>Employees</h1>
        {canEdit && (
          <div className="actions">
            <button className="btn" onClick={() => setImporting(true)}>Import CSV</button>
            <button className="btn primary" onClick={() => setEditing(EMPTY)}>Add employee</button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="filters">
          <input placeholder="Search name or ID" value={filters.q} onChange={updateFilter('q')} aria-label="Search" />
          <select value={filters.departmentId} onChange={updateFilter('departmentId')} aria-label="Department">
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
          <select value={filters.status} onChange={updateFilter('status')} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        {error && <p className="error">{error}</p>}
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>ID</th><th>Name</th><th>Department</th><th>Designation</th><th>Joined</th><th>Tenure</th><th>Rating</th><th>Status</th>
                {canEdit && <th />}
              </tr>
            </thead>
            <tbody>
              {data?.content.map((e) => (
                <tr key={e.id}>
                  <td>{e.employeeCode}</td>
                  <td>{e.fullName}</td>
                  <td>{e.departmentName}</td>
                  <td>{e.designation}</td>
                  <td>{e.joinDate}</td>
                  <td>{Math.floor(e.tenureMonths / 12)}y {e.tenureMonths % 12}m</td>
                  <td>{e.performanceRating ?? '—'}</td>
                  <td><span className={STATUS_CLASS[e.status]}>{STATUS_LABEL[e.status]}</span></td>
                  {canEdit && (
                    <td className="right nowrap">
                      <button className="btn small" onClick={() => setEditing({ ...e })}>Edit</button>{' '}
                      {e.status !== 'INACTIVE' && <button className="btn small" onClick={() => deactivate(e)}>Deactivate</button>}
                    </td>
                  )}
                </tr>
              ))}
              {data && data.content.length === 0 && (
                <tr><td colSpan={9} className="muted">No employees match these filters.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {data && data.totalPages > 1 && (
          <div className="pager">
            <button className="btn small" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span className="muted">Page {page + 1} of {data.totalPages} · {data.totalElements} employees</span>
            <button className="btn small" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        )}
        {data && data.totalPages <= 1 && <p className="muted small">{data.totalElements} employees</p>}
      </div>

      {importing && <ImportDialog onClose={() => setImporting(false)} onImported={load} />}
      {editing && (
        <EmployeeForm
          initial={editing}
          departments={departments}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            load()
          }}
        />
      )}
    </section>
  )
}
