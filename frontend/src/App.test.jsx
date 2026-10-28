import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App.jsx'

test('renders the navigation shell', () => {
  globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('{"status":"UP"}') })
  render(
    <MemoryRouter>
      <App />
    </MemoryRouter>,
  )
  expect(screen.getByText('Attrition Analysis')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Employees' })).toBeInTheDocument()
})
