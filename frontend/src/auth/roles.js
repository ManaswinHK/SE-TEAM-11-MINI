export const ROLE_LABEL = {
  HR_ADMIN: 'HR Admin',
  HR_MANAGER: 'HR Manager',
  DEPT_MANAGER: 'Department Manager',
  LEADERSHIP: 'Leadership',
}

// Which roles may open each page (AAS-F-002). The API enforces the same rules.
export const PAGE_ROLES = {
  dashboard: ['HR_ADMIN', 'HR_MANAGER', 'DEPT_MANAGER', 'LEADERSHIP'],
  employees: ['HR_ADMIN', 'HR_MANAGER', 'DEPT_MANAGER'],
  separations: ['HR_ADMIN', 'HR_MANAGER', 'DEPT_MANAGER'],
  settings: ['HR_ADMIN', 'HR_MANAGER', 'DEPT_MANAGER', 'LEADERSHIP'],
}
