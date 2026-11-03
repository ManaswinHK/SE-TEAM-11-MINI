import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { SEPARATION_STATUS } from '../labels.js'

// Exit clearance checklist for one separation (AAS-F-022). Closing is allowed only when every item is done.
export default function ClearanceDialog({ separation, canEdit, onClose, onChanged }) {
  const [checklist, setChecklist] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api(`/api/separations/${separation.id}/clearance`).then(setChecklist)
  }, [separation.id])

  async function run(request) {
    setError('')
    try {
      const next = await request()
      setChecklist(next)
      onChanged()
    } catch (err) {
      setError(err.message)
    }
  }

  const toggle = (item) => run(() => api(`/api/separations/${separation.id}/clearance/${item.id}`, {
    method: 'PUT',
    body: { done: !item.done },
  }))
  const closeSeparation = () => run(() => api(`/api/separations/${separation.id}/close`, { method: 'POST' }))

  const closed = checklist?.status === 'CLOSED'
  const editable = canEdit && checklist && !closed

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal">
        <h2>Exit clearance</h2>
        <p className="muted">
          {separation.employeeName} · {separation.employeeCode} · last working day {separation.lastWorkingDay}
        </p>
        {checklist && (
          <>
            <p>
              <span className={closed ? 'badge ok' : 'badge warn'}>{SEPARATION_STATUS[checklist.status]}</span>{' '}
              {checklist.done} of {checklist.total} items done
            </p>
            <ul className="checklist">
              {checklist.items.map((item) => (
                <li key={item.id}>
                  <label className="checkbox">
                    <input type="checkbox" checked={item.done} disabled={!editable} onChange={() => toggle(item)} />
                    {item.name}
                    {item.doneAt && <span className="muted small"> · {item.doneAt.slice(0, 10)}</span>}
                  </label>
                </li>
              ))}
            </ul>
          </>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>Done</button>
          {editable && (
            <button type="button" className="btn primary" onClick={closeSeparation}
              disabled={checklist.done < checklist.total}>
              Close separation
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
