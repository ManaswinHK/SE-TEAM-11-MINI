import { useEffect, useMemo, useState } from 'react'
import { BarElement, CategoryScale, Chart as ChartJS, LinearScale, Tooltip } from 'chart.js'
import { Bar } from 'react-chartjs-2'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip)

const BAR = '#2459d6'
const GRID = '#e3e7ef'
const INK = '#667085'

function iso(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function presetRange(key) {
  const now = new Date()
  const q = Math.floor(now.getMonth() / 3)
  switch (key) {
    case 'last12':
      return { from: iso(new Date(now.getFullYear() - 1, now.getMonth(), now.getDate() + 1)), to: iso(now) }
    case 'thisQuarter':
      return { from: iso(new Date(now.getFullYear(), q * 3, 1)), to: iso(now) }
    case 'lastQuarter':
      return { from: iso(new Date(now.getFullYear(), q * 3 - 3, 1)), to: iso(new Date(now.getFullYear(), q * 3, 0)) }
    case 'lastYear':
      return { from: `${now.getFullYear() - 1}-01-01`, to: `${now.getFullYear() - 1}-12-31` }
    default:
      return { from: `${now.getFullYear()}-01-01`, to: iso(now) }
  }
}

const PRESETS = [
  ['thisYear', 'This year'],
  ['last12', 'Last 12 months'],
  ['thisQuarter', 'This quarter'],
  ['lastQuarter', 'Last quarter'],
  ['lastYear', 'Last year'],
]

function Kpi({ label, value, hint }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {hint && <div className="kpi-hint">{hint}</div>}
    </div>
  )
}

function barOptions({ horizontal, suffix = '', tooltip }) {
  const valueAxis = {
    beginAtZero: true,
    grid: { color: GRID },
    border: { display: false },
    ticks: { color: INK, callback: (v) => `${v}${suffix}` },
  }
  const categoryAxis = { grid: { display: false }, border: { color: GRID }, ticks: { color: INK } }
  return {
    indexAxis: horizontal ? 'y' : 'x',
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: { callbacks: { label: tooltip } } },
    scales: horizontal ? { x: valueAxis, y: categoryAxis } : { x: categoryAxis, y: valueAxis },
  }
}

function barData(labels, values) {
  return {
    labels,
    datasets: [{ data: values, backgroundColor: BAR, hoverBackgroundColor: '#163f9e', borderRadius: 4, maxBarThickness: 22 }],
  }
}

export default function Dashboard() {
  const { user } = useAuth()
  const isManager = user.role === 'DEPT_MANAGER'
  const [preset, setPreset] = useState('thisYear')
  const [range, setRange] = useState(() => presetRange('thisYear'))
  const [departmentId, setDepartmentId] = useState('')
  const [departments, setDepartments] = useState([])
  const [report, setReport] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/api/departments').then(setDepartments)
  }, [])

  useEffect(() => {
    const params = new URLSearchParams(range)
    if (departmentId) params.set('departmentId', departmentId)
    api(`/api/analytics/attrition?${params}`)
      .then((r) => {
        setReport(r)
        setError('')
      })
      .catch((err) => setError(err.message))
  }, [range, departmentId])

  const deptName = useMemo(() => {
    const id = report?.departmentId
    return id ? departments.find((d) => d.id === id)?.name : null
  }, [report, departments])

  function choosePreset(key) {
    setPreset(key)
    if (key !== 'custom') setRange(presetRange(key))
  }

  const k = report?.kpis
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Attrition dashboard</h1>
          <p className="muted small">
            {deptName ? `${deptName} department` : 'Whole organization'}
            {k && ` · ${k.from} to ${k.to}`}
          </p>
        </div>
      </div>

      <div className="filters card">
        <select value={preset} onChange={(e) => choosePreset(e.target.value)} aria-label="Period">
          {PRESETS.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          <option value="custom">Custom range</option>
        </select>
        {preset === 'custom' && (
          <>
            <label className="inline">From<input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
            <label className="inline">To<input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
          </>
        )}
        {!isManager && (
          <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} aria-label="Department">
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {k && (
        <>
          <div className="kpis">
            <Kpi label="Attrition rate" value={`${k.attritionRate.toFixed(2)}%`} hint={`${k.separations} left ÷ ${k.averageHeadcount} avg headcount`} />
            <Kpi label="Voluntary attrition" value={`${k.voluntaryRate.toFixed(2)}%`} hint={`${k.voluntary} resignations`} />
            <Kpi label="Regretted attrition" value={`${k.regrettedRate.toFixed(2)}%`} hint={`${k.regretted} people we wanted to keep`} />
            <Kpi label="Headcount" value={k.closingHeadcount} hint={`${k.openingHeadcount} at start of period`} />
          </div>

          <div className="grid-2">
            {!report.departmentId && (
              <div className="card">
                <h2>Attrition rate by department</h2>
                <div className="chart" style={{ height: Math.max(180, report.byDepartment.length * 38) }}>
                  <Bar
                    data={barData(report.byDepartment.map((r) => r.key), report.byDepartment.map((r) => r.attritionRate))}
                    options={barOptions({
                      horizontal: true,
                      suffix: '%',
                      tooltip: (ctx) => {
                        const r = report.byDepartment[ctx.dataIndex]
                        return `${r.attritionRate}% · ${r.separations} left of ${r.averageHeadcount} avg`
                      },
                    })}
                  />
                </div>
              </div>
            )}
            <div className="card">
              <h2>Leavers by tenure</h2>
              <div className="chart" style={{ height: 220 }}>
                <Bar
                  data={barData(report.byTenure.map((r) => r.band), report.byTenure.map((r) => r.separations))}
                  options={barOptions({
                    horizontal: false,
                    tooltip: (ctx) => {
                      const r = report.byTenure[ctx.dataIndex]
                      return `${r.separations} people (${r.share}% of leavers)`
                    },
                  })}
                />
              </div>
            </div>
            <div className="card">
              <h2>By location</h2>
              <table className="table">
                <thead><tr><th>Location</th><th className="right">Left</th><th className="right">Avg headcount</th><th className="right">Attrition</th></tr></thead>
                <tbody>
                  {report.byLocation.map((r) => (
                    <tr key={r.key}>
                      <td>{r.key}</td>
                      <td className="right">{r.separations}</td>
                      <td className="right">{r.averageHeadcount}</td>
                      <td className="right">{r.attritionRate.toFixed(2)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!report.departmentId && (
              <div className="card">
                <h2>By department (table)</h2>
                <table className="table">
                  <thead><tr><th>Department</th><th className="right">Left</th><th className="right">Avg headcount</th><th className="right">Attrition</th></tr></thead>
                  <tbody>
                    {report.byDepartment.map((r) => (
                      <tr key={r.key}>
                        <td>{r.key}</td>
                        <td className="right">{r.separations}</td>
                        <td className="right">{r.averageHeadcount}</td>
                        <td className="right">{r.attritionRate.toFixed(2)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}
