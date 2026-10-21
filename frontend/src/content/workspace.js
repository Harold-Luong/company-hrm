import { defineContent } from '../i18n/content.js'
import { NOTIFICATION_BADGE_MAX } from '../config/presentation.js'

export const app = defineContent('app')

export const accountResultToasts = defineContent('accountResultToasts')

export const helpLayout = defineContent('helpLayout')

export const pageControls = defineContent('pageControls', { methods: { summary: true } })

export const referencePicker = defineContent('referencePicker')

export const workspaceLayout = defineContent('workspaceLayout', {
    methods: {
        badgeCount: (count) => ({
            count: count > NOTIFICATION_BADGE_MAX ? `${NOTIFICATION_BADGE_MAX}+` : count,
        }),
        pendingAttendance: true,
        pendingLeave: true,
        copyright: true,
    },
})

export const routeTitles = defineContent('routeTitles', {
    methods: { createResource: true, resourceDetail: true },
})

export const featureReference = defineContent('featureReference', {
    methods: { resultCount: true, openFeatureLabel: true, roleRequired: true },
})

export const forbidden = defineContent('forbidden')

export const home = defineContent('home', { methods: { greeting: true } })

export const notFound = defineContent('notFound')

export const userGuide = defineContent('userGuide', { methods: { openFeatureLabel: true } })
