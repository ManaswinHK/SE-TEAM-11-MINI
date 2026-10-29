import { NavLink, Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { PAGE_ROLES, ROLE_LABEL } from '../auth/roles.js'

const NAV = [
  { to: '/', label: 'Dashboard', page: 'dashboard', end: true },
  { to: '/employees', label: 'Employees', page: 'employees' },
  { to: '/separations', label: 'Separations', page: 'separations' },
  { to: '/settings', label: 'Settings', page: 'settings' },
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
          {NAV.filter((n) => PAGE_ROLES[n.page].includes(user.role)).map((n) => (
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

/** Route guard: users without access to a page are sent back to the dashboard. */
export function RequirePage({ page, children }) {
  const { user } = useAuth()
  return PAGE_ROLES[page].includes(user?.role) ? children : <Navigate to="/" replace />
}
