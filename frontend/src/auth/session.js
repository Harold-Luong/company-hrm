import { computed, reactive, readonly } from 'vue'
import { ApiError, authRequest } from './api.js'
const storageKey = 'company-hrm.refresh-token'
const state = reactive({ user: null, initialized: false, signingOut: false })
let accessToken = null
let refreshToken = sessionStorage.getItem(storageKey)
let revision = 0
let refreshing = null
let initializing = null
function storeTokens(tokens) {
    if (!tokens.accessToken || !tokens.refreshToken) throw new ApiError(502, 'Invalid token response')
    // Store only the rotating refresh token for this tab. Access token and roles stay in memory.
    sessionStorage.setItem(storageKey, tokens.refreshToken)
    accessToken = tokens.accessToken
    refreshToken = tokens.refreshToken
}
function clearSession() {
    revision += 1
    accessToken = null
    refreshToken = null
    state.user = null
    sessionStorage.removeItem(storageKey)
}
function isSessionRejected(error) {
    return error instanceof ApiError && [400, 401, 403, 404].includes(error.status)
}
async function refresh() {
    if (refreshing) return refreshing
    if (!refreshToken) throw new ApiError(401, 'No session')
    const currentRevision = revision
    const token = refreshToken
    refreshing = (async () => {
        try {
            const tokens = await authRequest('/refresh', {
                method: 'POST',
                body: JSON.stringify({ refreshToken: token }),
            })
            if (currentRevision !== revision) throw new ApiError(401, 'Session changed')
            storeTokens(tokens)
        } catch (error) {
            if (currentRevision === revision && isSessionRejected(error)) clearSession()
            throw error
        }
    })().finally(() => {
        refreshing = null
    })
    return refreshing
}
async function authorizedRequest(path, init = {}) {
    if (state.signingOut) throw new ApiError(401, 'Signing out')
    const currentRevision = revision
    if (!accessToken) await refresh()
    const usedToken = accessToken
    const send = () => {
        if (currentRevision !== revision || state.signingOut) throw new ApiError(401, 'Session changed')
        return authRequest(path, {
            ...init,
            headers: { ...init.headers, Authorization: `Bearer ${accessToken}` },
        })
    }
    try {
        const data = await send()
        if (currentRevision !== revision) throw new ApiError(401, 'Session changed')
        return data
    } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401 || currentRevision !== revision)
            throw error
        // Concurrent 401s share one refresh; late responses reuse the already refreshed token.
        if (usedToken === accessToken) await refresh()
        try {
            const data = await send()
            if (currentRevision !== revision) throw new ApiError(401, 'Session changed')
            return data
        } catch (retryError) {
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
async function loadUser() {
    const currentRevision = revision
    try {
        const user = await authorizedRequest('/me')
        if (!user.active) throw new ApiError(403, 'User is inactive')
        if (!Array.isArray(user.roles) || typeof user.email !== 'string')
            throw new ApiError(502, 'Invalid user response')
        if (currentRevision !== revision) throw new ApiError(401, 'Session changed')
        state.user = user
    } catch (error) {
        if (currentRevision === revision && isSessionRejected(error)) clearSession()
        throw error
    }
}
async function initialize() {
    if (state.initialized) return
    if (initializing) return initializing
    initializing = (async () => {
        try {
            if (refreshToken) await loadUser()
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
    const response = await authRequest('/login', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), password }),
    })
    clearSession()
    try {
        storeTokens(response.data)
        await loadUser()
        state.initialized = true
    } catch (error) {
        clearSession()
        throw error
    }
}
async function logout(allSessions = false) {
    if (state.signingOut) return
    // Finish any rotation so logout revokes the latest refresh token.
    if (refreshing) await refreshing.catch(() => undefined)
    if (allSessions) {
        await authorizedRequest('/logout-all', { method: 'POST' })
        clearSession()
        return
    }
    const token = refreshToken
    state.signingOut = true
    clearSession()
    try {
        if (token)
            await authRequest('/logout', {
                method: 'POST',
                body: JSON.stringify({ refreshToken: token }),
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
}
