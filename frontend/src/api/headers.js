export const versionHeader = (version) => ({ 'If-Match': `"${version}"` })
export const idempotencyHeader = (key) => ({ 'Idempotency-Key': key })
