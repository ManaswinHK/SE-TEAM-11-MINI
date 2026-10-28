import { Route, Routes } from 'react-router-dom'
import Layout, { RequirePage } from './components/Layout.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Employees from './pages/Employees.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Separations from './pages/Separations.jsx'
import Settings from './pages/Settings.jsx'
import { AuthProvider } from './auth/AuthContext.jsx'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />
        <Route element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="employees" element={<RequirePage page="employees"><Employees /></RequirePage>} />
          <Route path="separations" element={<RequirePage page="separations"><Separations /></RequirePage>} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
