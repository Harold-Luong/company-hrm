import { defineContent } from '../i18n/content.js'
import { dateLimits, fieldLimits } from '../config/validation.js'
import { getDisplayLocale } from '../i18n/index.js'

export const leaveAdjustment = defineContent('leaveAdjustment', {
    methods: {
        approvedSummary: true,
        remainingSummary: true,
        refundSummary: true,
        reasonSummary: true,
        reviewSummary: true,
    },
})

export const profileLeave = defineContent('profileLeave', {
    methods: { balanceYear: true, durationDays: true },
    parameters: { invalidYear: () => ({ minYear: dateLimits.minYear, maxYear: dateLimits.maxYear }) },
})

export const leaveErrors = defineContent('leaveErrors', {
    parameters: {
        invalidPeriod: () => ({ maxRangeDays: dateLimits.maxRangeDays }),
        invalidDateRange: () => ({ maxRangeDays: dateLimits.maxRangeDays }),
    },
})

export const leaveRules = defineContent('leaveRules', {
    parameters: {
        invalidDateRange: () => ({ maxRangeDays: dateLimits.maxRangeDays }),
        invalidReason: () => ({
            leaveReason: fieldLimits.leaveReason.toLocaleString(getDisplayLocale()),
        }),
    },
})

export const leaveRequestDetail = defineContent('leaveRequestDetail', { methods: { actor: true } })

export const leaveRequests = defineContent('leaveRequests', { methods: { balanceSummary: true } })
