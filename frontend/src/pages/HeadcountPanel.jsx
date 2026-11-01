import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'

function isoDate(d) {
  return d.toISOString().slice(0, 10)
}

/** Monthly headcount snapshots (AAS-F-013); HR Admins can take one immediately. */
export default function HeadcountPanel() {
  const { hasRole } = useAuth()
  const [rows, setRows] = useState([])
  const [msg, setMsg] = useState('')

  const load = useCallback(() => {
    const to = new Date()
    const from = new Date(to.getFullYear() - 1, to.getMonth(), 1)
    api(`/api/headcount/snapshots/totals?from=${isoDate(from)}&to=${isoDate(to)}`).then((r) => setRows(r.slice().reverse()))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function takeNow() {
    const r = await api('/api/headcount/snapshots', { method: 'POST' })
    setMsg(`Snapshot taken: ${r.reduce((sum, x) => sum + x.headcount, 0)} employees across ${r.length} departments.`)
    load()
  }

  return (
    <div className="card">
      <div className="page-head">
        <h2>Headcount snapshots</h2>
        {hasRole('HR_ADMIN') && <button className="btn small" onClick={takeNow}>Take snapshot now</button>}
      </div>
      <p className="muted small">Taken automatically on the 1st of every month and used to calculate average headcount.</p>
      {msg && <p className="success">{msg}</p>}
      <table className="table">
        <thead><tr><th>Date</th><th className="right">Headcount</th></tr></thead>
        <tbody>
          {rows.map((r) => <tr key={r.date}><td>{r.date}</td><td className="right">{r.headcount}</td></tr>)}
          {rows.length === 0 && <tr><td colSpan={2} className="muted">No snapshots yet</td></tr>}
        </tbody>
      </table>
    </div>
  )
}
