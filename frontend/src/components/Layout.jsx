import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

const ROLE_LABEL = {
  HR_ADMIN: 'HR Admin',
  HR_MANAGER: 'HR Manager',
  DEPT_MANAGER: 'Department Manager',
  LEADERSHIP: 'Leadership',
}

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/employees', label: 'Employees' },
  { to: '/separations', label: 'Separations' },
  { to: '/settings', label: 'Settings' },
]

export default function Layout() {
  const { user, logout } = useAuth()
  if (!user) return <Navigate to="/login" replace />

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">A</span>
          <span>Attrition Analysis</span>
        </div>
        <nav>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-box">
          <div className="user-name">{user.fullName}</div>
          <div className="user-meta">{ROLE_LABEL[user.role]} · {user.orgName}</div>
          <button className="btn ghost small" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
