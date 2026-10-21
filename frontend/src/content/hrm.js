import { defineContent } from '../i18n/content.js'
import { provisioningLookup } from '../config/runtime.js'

export const activationInvitation = defineContent('activationInvitation', {
    methods: {
        resendCountdown: true,
        expiresAt: true,
        acceptedAt: true,
        attempts: true,
        errorCode: true,
    },
})

export const myProfileCard = defineContent('myProfileCard')

export const provisioning = defineContent('provisioning', {
    methods: { checkedAt: true },
    parameters: {
        pollingNotice: () => ({
            intervalSeconds: provisioningLookup.intervalMs / 1000,
            maxAttempts: provisioningLookup.maxAttempts,
        }),
    },
})

export const account = defineContent('account')

export const catalogForm = defineContent('catalogForm', {
    methods: { back: true, code: true, name: true },
})

export const directory = defineContent('directory', {
    methods: { create: true, listTitle: true, empty: true, tableLabel: true },
})

export const employeeForm = defineContent('employeeForm')
