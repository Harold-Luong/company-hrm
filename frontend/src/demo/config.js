// Only the dedicated Vite mode enables the offline adapter; production stays on real APIs.
export const demoEnabled = import.meta.env.MODE === 'demo'
export const demoStorageKey = 'company-hrm.demo.v1'
export const demoPassword = 'Demo@123456'
export const demoPersonas = [
  { role: 'ADMIN', email: 'admin@demo.example' },
  { role: 'HR', email: 'hr@demo.example' },
  { role: 'EMPLOYEE', email: 'employee@demo.example' },
]
