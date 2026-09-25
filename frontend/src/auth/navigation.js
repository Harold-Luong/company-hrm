export const accountCreationRoles = ['HR', 'ADMIN']
// Only known internal destinations are accepted after login or a connection retry.
export function safeDestination(value) {
    return typeof value === 'string' &&
        ['/', '/account', '/accounts/new', '/forbidden'].includes(value)
        ? value
        : '/'
}
