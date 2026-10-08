import { computed, reactive, readonly } from 'vue'
import { ApiError, authRequest, apiRequest } from './api.js'
const storageKey = 'company-hrm.session'
const lockName = 'company-hrm.session-mutation'
const state = reactive({ user: null, initialized: false, signingOut: false })
let accessToken = null
let session = readSession()
let revision = 0
let refreshing = null
let initializing = null
// Old per-tab tokens cannot safely be reused after another tab has rotated them.
sessionStorage.removeItem('company-hrm.refresh-token')
function readSession() {
    const value = localStorage.getItem(storageKey)
    if (!value) return null
    try {
        const stored = JSON.parse(value)
        return typeof stored?.id === 'string' &&
            typeof stored?.refreshToken === 'string' &&
            stored.id &&
            stored.refreshToken
            ? stored
            : null
    } catch {
        return null
    }
}
function syncSession() {
    const stored = readSession()
    if (stored?.id !== session?.id) {
        revision += 1
        accessToken = null
        state.user = null
        state.initialized = false
    }
    session = stored
}
function assertRevision(expected) {
    syncSession()
    if (revision !== expected) throw new ApiError(401, 'Session changed')
}
async function withSessionLock(callback) {
    if (!navigator.locks?.request)
        throw new ApiError(503, 'Shared session requires HTTPS and Web Locks support')
    return navigator.locks.request(lockName, callback)
}
window.addEventListener('storage', (event) => {
    if (event.storageArea === localStorage && (event.key === storageKey || event.key === null))
        syncSession()
})
window.addEventListener('focus', syncSession)
function storeTokens(tokens, id = session?.id) {
    if (!tokens.accessToken || !tokens.refreshToken) throw new ApiError(502, 'Invalid token response')
    // One atomic record identifies the login separately from its rotating token.
    localStorage.setItem(storageKey, JSON.stringify({ id, refreshToken: tokens.refreshToken }))
    syncSession()
    accessToken = tokens.accessToken
}
function clearSession() {
    localStorage.removeItem(storageKey)
    syncSession()
    revision += 1
    accessToken = null
    state.user = null
}
function isSessionRejected(error) {
    return error instanceof ApiError && [400, 401, 403, 404].includes(error.status)
}
async function refresh() {
    if (refreshing) return refreshing
    syncSession()
    if (!session) throw new ApiError(401, 'No session')
    const currentRevision = revision
    refreshing = withSessionLock(async () => {
        assertRevision(currentRevision)
        // Read after acquiring the lock: another tab may have rotated the token.
        const token = session.refreshToken
        try {
            const tokens = await authRequest('/refresh', {
                method: 'POST',
                body: JSON.stringify({ refreshToken: token }),
            })
            assertRevision(currentRevision)
            storeTokens(tokens)
        } catch (error) {
            syncSession()
            if (currentRevision === revision && isSessionRejected(error)) clearSession()
            throw error
        }
    }).finally(() => {
        refreshing = null
    })
    return refreshing
}
async function authorizedRequest(path, init = {}, request = authRequest) {
    syncSession()
    if (state.signingOut) throw new ApiError(401, 'Signing out')
    const currentRevision = revision
    if (!accessToken) await refresh()
    const usedToken = accessToken
    const send = () => {
        assertRevision(currentRevision)
        if (currentRevision !== revision || state.signingOut) throw new ApiError(401, 'Session changed')
        return request(path, {
            ...init,
            headers: { ...init.headers, Authorization: `Bearer ${accessToken}` },
        })
    }
    try {
        const data = await send()
        assertRevision(currentRevision)
        return data
    } catch (error) {
        syncSession()
        if (!(error instanceof ApiError) || error.status !== 401 || currentRevision !== revision)
            throw error
        // Concurrent 401s share one refresh; late responses reuse the already refreshed token.
        if (usedToken === accessToken) await refresh()
        try {
            const data = await send()
            assertRevision(currentRevision)
            return data
        } catch (retryError) {
            syncSession()
            if (
                currentRevision === revision &&
                retryError instanceof ApiError &&
                retryError.status === 401
            )
                clearSession()
            throw retryError
        }
    }
}
// Load the current user and store it in state.user. If the session is invalid, clear it.
async function loadUser() {
    syncSession()
    const currentRevision = revision
    try {
        const user = await authorizedRequest('/me')
        if (!user.active) throw new ApiError(403, 'User is inactive')
        if (!Array.isArray(user.roles) || typeof user.email !== 'string')
            throw new ApiError(502, 'Invalid user response')
        assertRevision(currentRevision) 
        state.user = user
    } catch (error) {
        syncSession()
        if (currentRevision === revision && isSessionRejected(error)) clearSession()
        throw error
    }
}
async function initialize() {
    syncSession()
    if (state.initialized) return
    if (initializing) return initializing
    initializing = (async () => {
        try {
            if (session) await loadUser()
            state.initialized = true
        } catch (error) {
            if (!isSessionRejected(error)) throw error
            state.initialized = true
        }
    })().finally(() => {
        initializing = null
    })
    return initializing
}
async function login(email, password) {
    await withSessionLock(async () => {
        const response = await authRequest('/login', {
            method: 'POST',
            body: JSON.stringify({ email: email.trim(), password }),
        })
        storeTokens(response.data, crypto.randomUUID())
    })
    const currentRevision = revision
    try {
        await loadUser()
        state.initialized = true
    } catch (error) {
        syncSession()
        if (currentRevision === revision) clearSession()
        throw error
    }
}
async function logout(allSessions = false) {
    if (state.signingOut) return
    syncSession()
    const currentRevision = revision
    // Finish any rotation so logout revokes the latest refresh token.
    if (refreshing) await refreshing.catch(() => undefined)
    if (allSessions) {
        await authorizedRequest('/logout-all', { method: 'POST' })
        assertRevision(currentRevision)
        clearSession()
        return
    }
    state.signingOut = true
    try {
        await withSessionLock(async () => {
            assertRevision(currentRevision)
            const token = session?.refreshToken
            clearSession()
            if (token)
                await authRequest('/logout', {
                    method: 'POST',
                    body: JSON.stringify({ refreshToken: token }),
                })
        })
    } catch (error) {
        // An expired/revoked refresh session is already signed out.
        if (!isSessionRejected(error)) throw error
    } finally {
        state.signingOut = false
    }
}
async function register(input) {
    return authorizedRequest('/register', {
        method: 'POST',
        body: JSON.stringify(input),
    })
}
export const auth = {
    state: readonly(state),
    authenticated: computed(() => !!state.user),
    hasRole: (allowed) => !!state.user?.roles.some((role) => allowed.includes(role)),
    initialize,
    login,
    logout,
    loadUser,
    register,
    request: (path, init) => authorizedRequest(path, init, apiRequest),
}
