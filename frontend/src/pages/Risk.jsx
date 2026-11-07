import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { BAND_CLASS, BAND_LABEL } from '../labels.js'

// Weekly flight-risk scores (AAS-F-050), highest first.
export default function Risk() {
  const { hasRole } = useAuth()
  const isHr = hasRole('HR_ADMIN', 'HR_MANAGER')
  const [band, setBand] = useState('')
  const [rows, setRows] = useState(null)
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    api(`/api/risk${band ? `?band=${band}` : ''}`).then(setRows)
  }, [band])

  useEffect(() => {
    load()
  }, [load])

  async function runNow() {
    const r = await api('/api/risk/run', { method: 'POST' })
    setNotice(`Scored ${r.scored} active employees.`)
    load()
  }

  const counts = rows ? Object.fromEntries(Object.keys(BAND_LABEL).map((b) => [b, rows.filter((r) => r.band === b).length])) : {}

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Flight risk</h1>
          <p className="muted small">
            Scored weekly from tenure, time since last promotion, performance rating and team attrition.
            {rows?.[0] && ` Last run ${rows[0].scoredOn}.`}
          </p>
        </div>
        {isHr && <button className="btn primary" onClick={runNow}>Score now</button>}
      </div>
      {notice && <p className="success">{notice}</p>}
      <div className="card">
        <div className="filters">
          <select value={band} onChange={(e) => setBand(e.target.value)} aria-label="Band">
            <option value="">All bands</option>
            {Object.entries(BAND_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          {!band && rows && (
            <span className="muted small">
              {counts.HIGH} high · {counts.MEDIUM} medium · {counts.LOW} low
            </span>
          )}
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Employee</th><th>Department</th><th className="right">Score</th><th>Band</th><th>Top factors</th></tr>
            </thead>
            <tbody>
              {rows?.map((r) => (
                <tr key={r.employeeId}>
                  <td>{r.employeeName}<div className="muted small">{r.employeeCode}</div></td>
                  <td>{r.departmentName}</td>
                  <td className="right">{r.score}</td>
                  <td><span className={BAND_CLASS[r.band]}>{BAND_LABEL[r.band]}</span></td>
                  <td>
                    {/* AAS-F-051: what drives the score, with each factor's share of it */}
                    <ul className="factors">
                      {r.factors.map((f) => (
                        <li key={f.label}>{f.label} <span className="muted small">{f.share}%</span></li>
                      ))}
                    </ul>
                  </td>
                </tr>
              ))}
              {rows && rows.length === 0 && <tr><td colSpan={5} className="muted">No scores yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
