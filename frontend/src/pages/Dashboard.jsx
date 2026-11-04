import { useEffect, useMemo, useState } from 'react'
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import { Bar, Line } from 'react-chartjs-2'
import { api } from '../api.js'
import { useAuth } from '../auth/AuthContext.jsx'

ChartJS.register(CategoryScale, LinearScale, BarElement, LineElement, PointElement, Legend, Tooltip)

const BAR = '#2459d6'
const GRID = '#e3e7ef'
const INK = '#667085'
const PREVIOUS = '#98a2b3'
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const monthLabel = (ym) => `${MONTHS[Number(ym.slice(5, 7)) - 1]} ${ym.slice(2, 4)}`

// 12-month trend against the same months a year earlier (AAS-F-042).
function TrendChart({ points }) {
  const data = {
    // The current month is still running, so its point covers only the days so far.
    labels: points.map((p, i) => (i === points.length - 1 ? `${monthLabel(p.month)} (to date)` : monthLabel(p.month))),
    datasets: [
      { label: 'Last 12 months', data: points.map((p) => p.attritionRate), borderColor: BAR, backgroundColor: BAR, tension: 0.25, pointRadius: 3 },
      { label: 'Previous year', data: points.map((p) => p.previousYearRate), borderColor: PREVIOUS, backgroundColor: PREVIOUS, borderDash: [5, 4], tension: 0.25, pointRadius: 2 },
    ],
  }
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { position: 'bottom', labels: { color: INK, boxWidth: 12 } },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const p = points[ctx.dataIndex]
            return ctx.datasetIndex === 0
              ? `This year: ${p.attritionRate}% (${p.separations} left)`
              : `Previous year: ${p.previousYearRate}% (${p.previousYearSeparations} left)`
          },
        },
      },
    },
    scales: {
      x: { grid: { display: false }, border: { color: GRID }, ticks: { color: INK } },
      y: { beginAtZero: true, grid: { color: GRID }, border: { display: false }, ticks: { color: INK, callback: (v) => `${v}%` } },
    },
  }
  return <Line data={data} options={options} />
}

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

const SURVEY_AREAS = [
  ['manager', 'Manager'],
  ['pay', 'Pay and benefits'],
  ['growth', 'Career growth'],
  ['workLife', 'Work-life balance'],
  ['culture', 'Culture'],
]

// Average 1-5 ratings; hidden below the anonymity threshold (AAS-SR-006).
function SurveyScores({ survey }) {
  if (survey.insufficientData) {
    return (
      <p className="muted">
        {survey.responses === 0 ? 'No survey responses in this period.' : 'Insufficient data: scores appear from 5 responses.'}
      </p>
    )
  }
  return (
    <>
      <div className="chart" style={{ height: 200 }}>
        <Bar
          data={barData(SURVEY_AREAS.map(([, label]) => label), SURVEY_AREAS.map(([key]) => survey[key]))}
          options={barOptions({ horizontal: true, tooltip: (ctx) => `${ctx.parsed.x.toFixed(2)} out of 5` })}
        />
      </div>
      <p className="muted small">Average of {survey.responses} responses, from 1 (poor) to 5 (excellent)</p>
    </>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const isManager = user.role === 'DEPT_MANAGER'
  const [preset, setPreset] = useState('thisYear')
  const [range, setRange] = useState(() => presetRange('thisYear'))
  const [departmentId, setDepartmentId] = useState('')
  const [departments, setDepartments] = useState([])
  const [report, setReport] = useState(null)
  const [trend, setTrend] = useState(null)
  const [insights, setInsights] = useState(null)
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
    api(`/api/analytics/exit-reasons?${params}`).then(setInsights).catch(() => setInsights(null))
  }, [range, departmentId])

  // The trend always covers the last 12 months, whatever period the KPIs show.
  useEffect(() => {
    api(`/api/analytics/trend?months=12${departmentId ? `&departmentId=${departmentId}` : ''}`).then(setTrend)
  }, [departmentId])

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
            <Kpi label="Early attrition" value={`${k.earlyAttritionRate.toFixed(2)}%`} hint={`${k.earlyLeavers} left within a year (${k.earlyLeaverShare}% of leavers)`} />
            <Kpi label="Headcount" value={k.closingHeadcount} hint={`${k.openingHeadcount} at start of period`} />
          </div>

          {trend && (
            <div className="card">
              <h2>Monthly attrition, last 12 months vs previous year</h2>
              <div className="chart" style={{ height: 260 }}>
                <TrendChart points={trend} />
              </div>
            </div>
          )}

          {insights && (
            <div className="grid-2">
              <div className="card">
                <h2>Top exit reasons</h2>
                {insights.reasons.length === 0 ? <p className="muted">No exits in this period.</p> : (
                  <table className="table">
                    <thead><tr><th>Reason</th><th className="right">Left</th><th className="right">Share</th></tr></thead>
                    <tbody>
                      {insights.reasons.map((r) => (
                        <tr key={r.reason}>
                          <td>{r.reason}</td>
                          <td className="right">{r.separations}</td>
                          <td className="right">{Math.round(r.share)}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              <div className="card">
                <h2>Exit survey scores</h2>
                <SurveyScores survey={insights.survey} />
              </div>
            </div>
          )}

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
