import { useState } from 'react'
import { api, download } from '../api.js'

/** Bulk CSV import with a row-by-row error report (AAS-F-011). */
export default function ImportDialog({ onClose, onImported }) {
  const [file, setFile] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const form = new FormData()
      form.append('file', file)
      const r = await api('/api/employees/import', { method: 'POST', body: form })
      setResult(r)
      if (r.imported > 0) onImported()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal">
        <h2>Import employees from CSV</h2>
        <p className="muted small">
          Up to 10,000 rows. Required columns: employeeCode, fullName, department, joinDate (YYYY-MM-DD).
          Department names must match Settings. Rows with errors are skipped and listed below.{' '}
          <button type="button" className="btn link" onClick={() => download('/api/employees/import/template', 'employee-import-template.csv')}>
            Download template
          </button>
        </p>
        <form onSubmit={submit} className="inline-form">
          <input type="file" accept=".csv,text/csv" onChange={(e) => { setFile(e.target.files[0]); setResult(null) }} aria-label="CSV file" required />
          <button className="btn primary" disabled={!file || busy}>{busy ? 'Importing…' : 'Import'}</button>
        </form>
        {error && <p className="error">{error}</p>}
        {result && (
          <>
            <p className={result.rejected ? 'error' : 'success'}>
              {result.imported} of {result.totalRows} rows imported{result.rejected ? `, ${result.rejected} rejected` : ''}.
            </p>
            {result.errors.length > 0 && (
              <div className="table-wrap scroll-y">
                <table className="table">
                  <thead><tr><th>Row</th><th>Employee ID</th><th>Problem</th></tr></thead>
                  <tbody>
                    {result.errors.map((er) => (
                      <tr key={er.row}><td>{er.row}</td><td>{er.employeeCode || '—'}</td><td>{er.message}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
        <div className="modal-actions">
          <button className="btn" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
