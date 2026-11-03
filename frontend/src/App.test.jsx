import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'

beforeEach(() => sessionStorage.clear())

test('signed-out users see the sign-in form', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  )
  expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(screen.getByLabelText('Password')).toBeInTheDocument()
})

test('signed-in users see the navigation with their role', () => {
  sessionStorage.setItem('aas.session', JSON.stringify({
    accessToken: 't', expiresAt: Date.now() + 30 * 60 * 1000,
    user: { fullName: 'Hari', role: 'HR_MANAGER', orgName: 'Acme' },
  }))
  globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') })
  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  )
  expect(screen.getByRole('link', { name: 'Employees' })).toBeInTheDocument()
  expect(screen.getByText(/HR Manager · Acme/)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Attrition dashboard' })).toBeInTheDocument()
})

test('leadership does not see employee-level pages', () => {
  sessionStorage.setItem('aas.session', JSON.stringify({
    accessToken: 't', expiresAt: Date.now() + 30 * 60 * 1000,
    user: { fullName: 'Lakshmi', role: 'LEADERSHIP', orgName: 'Acme' },
  }))
  globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') })
  render(
    <MemoryRouter initialEntries={['/employees']}>
      <App />
    </MemoryRouter>,
  )
  expect(screen.queryByRole('link', { name: 'Employees' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Attrition dashboard' })).toBeInTheDocument()
})
