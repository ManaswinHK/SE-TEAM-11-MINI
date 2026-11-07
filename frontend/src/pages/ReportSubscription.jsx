import { useEffect, useState } from 'react'
import { api } from '../api.js'

// Opt in to the monthly attrition summary email (AAS-F-062).
export default function ReportSubscription() {
  const [subscribed, setSubscribed] = useState(null)

  useEffect(() => {
    api('/api/reports/subscription').then((r) => setSubscribed(r.subscribed))
  }, [])

  async function toggle(e) {
    const r = await api('/api/reports/subscription', { method: 'PUT', body: { subscribed: e.target.checked } })
    setSubscribed(r.subscribed)
  }

  return (
    <div className="card">
      <h2>Monthly report</h2>
      <label className="checkbox">
        <input type="checkbox" checked={!!subscribed} disabled={subscribed === null} onChange={toggle} />
        Email me last month's attrition summary on the 2nd of each month
      </label>
    </div>
  )
}
