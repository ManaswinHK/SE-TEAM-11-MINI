import { useState } from 'react'
import { api } from '../api.js'

const today = () => new Date().toISOString().slice(0, 10)

// Log a retention action for an at-risk employee (AAS-F-052).
export function LogActionDialog({ employee, onCancel, onSaved }) {
  const [form, setForm] = useState({ action: '', ownerName: '', dueDate: '' })
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      onSaved(await api('/api/retention-actions', { method: 'POST', body: { ...form, employeeId: employee.employeeId } }))
    } catch (err) {
      setError(err.body?.fieldErrors?.[0]?.message ?? err.message)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <form className="modal" onSubmit={submit}>
        <h2>Retention action</h2>
        <p className="muted">{employee.employeeName} · {employee.departmentName} · risk {employee.score}</p>
        <label>
          Action
          <input list="action-ideas" maxLength={200} value={form.action} onChange={set('action')} required autoFocus />
          <datalist id="action-ideas">
            <option value="Career discussion" />
            <option value="Compensation review" />
            <option value="Promotion review" />
            <option value="Workload review" />
          </datalist>
        </label>
        <div className="grid-3">
          <label>Owner<input maxLength={150} value={form.ownerName} onChange={set('ownerName')} required /></label>
          <label>Due date<input type="date" min={today()} value={form.dueDate} onChange={set('dueDate')} required /></label>
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

export function RetentionList({ actions, onChanged }) {
  async function complete(a) {
    const outcome = window.prompt(`Outcome of "${a.action}" for ${a.employeeName}?`)
    if (outcome === null) return
    await api(`/api/retention-actions/${a.id}/complete`, { method: 'POST', body: { outcome } })
    onChanged()
  }

  if (actions.length === 0) return <p className="muted">No retention actions logged yet.</p>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr><th>Employee</th><th>Action</th><th>Owner</th><th>Due</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {actions.map((a) => (
            <tr key={a.id} className={a.overdue ? 'overdue' : undefined}>
              <td>{a.employeeName}<div className="muted small">{a.departmentName}</div></td>
              <td>{a.action}{a.outcome && <div className="muted small">{a.outcome}</div>}</td>
              <td>{a.ownerName}</td>
              <td>{a.dueDate}</td>
              <td>
                {a.status === 'DONE' && <span className="badge ok">Done</span>}
                {a.status === 'OPEN' && <span className={a.overdue ? 'badge danger' : 'badge warn'}>{a.overdue ? 'Overdue' : 'Open'}</span>}
              </td>
              <td>{a.status === 'OPEN' && <button className="btn link" onClick={() => complete(a)}>Mark done</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
