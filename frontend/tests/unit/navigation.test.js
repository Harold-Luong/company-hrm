import { describe, expect, it } from 'vitest'
import { safeDestination } from '../../src/auth/navigation.js'
describe('post-login destinations', () => {
  it.each([
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    '/login',
    '/accounts/new?redirect=https://evil.example',
    undefined,
    ['/account'],
  ])('rejects untrusted redirect %s', (value) => {
    expect(safeDestination(value)).toBe('/')
  })
  it.each(['/account', '/accounts/new', '/forbidden', '/'])(
    'allows known application page %s',
    (value) => {
      expect(safeDestination(value)).toBe(value)
    },
  )
})
