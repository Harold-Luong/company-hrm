import { describe, expect, it } from 'vitest'
import { safeDestination } from '../../src/auth/navigation.js'
describe('post-login destinations', () => {
    it.each([
        'https://evil.example',
        '//evil.example',
        '/\\evil.example',
        '/login',
        '/employees/../../login',
        '/employees/not-a-uuid',
        '/departments/new?redirect=https://evil.example',
        '/accounts/new?redirect=https://evil.example',
        undefined,
        ['/account'],
    ])('rejects untrusted redirect %s', (value) => {
        expect(safeDestination(value)).toBe('/')
    })
    it.each([
        '/account',
        '/accounts/new',
        '/forbidden',
        '/',
        '/employees',
        '/positions/new',
        '/departments/550e8400-e29b-41d4-a716-446655440000',
        '/services',
        '/calendar',
    ])('allows known application page %s', (value) => {
        expect(safeDestination(value)).toBe(value)
    })
})
