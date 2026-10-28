import { useEffect, useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import { api } from './api.js'

function Placeholder({ title }) {
  return (
    <section>
      <h1>{title}</h1>
      <p className="muted">Coming in Sprint 1.</p>
    </section>
  )
}

function Home() {
  const [health, setHealth] = useState(null)
  useEffect(() => {
    api('/api/health').then(setHealth).catch(() => setHealth({ status: 'DOWN' }))
  }, [])
  return (
    <section>
      <h1>Dashboard</h1>
      <p className="muted">API status: {health ? health.status : 'checking…'}</p>
    </section>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="employees" element={<Placeholder title="Employees" />} />
        <Route path="separations" element={<Placeholder title="Separations" />} />
        <Route path="settings" element={<Placeholder title="Settings" />} />
      </Route>
    </Routes>
  )
}
