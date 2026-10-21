import { defineContent } from '../i18n/content.js'
import { activationPassword } from '../config/validation.js'

export const roleNames = defineContent('roleNames')

export const authAccount = defineContent('authAccount')

export const activation = defineContent('activation', {
    methods: { copyright: true },
    parameters: {
        invalidPassword: () => ({
            minLength: activationPassword.minLength,
            maxBytes: activationPassword.maxBytes,
        }),
        passwordHint: () => ({
            minLength: activationPassword.minLength,
            maxBytes: activationPassword.maxBytes,
        }),
    },
})

export const connectionError = defineContent('connectionError')

export const createAccount = defineContent('createAccount', { methods: { created: true } })

export const login = defineContent('login', { methods: { copyright: true } })
