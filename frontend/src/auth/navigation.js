export const accountCreationRoles = ['HR', 'ADMIN']
// Only known internal destinations are accepted after login or a connection retry.
export function safeDestination(value) {
    return typeof value === 'string' &&
        (['/', '/account', '/accounts/new', '/forbidden', '/services', '/calendar'].includes(value) ||
            /^\/(employees|departments|positions)(\/(new|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}))?$/i.test(
                value,
            ))
        ? value
        : '/'
}
