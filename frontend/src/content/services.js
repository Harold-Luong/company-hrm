import { defineContent } from '../i18n/content.js'

export const serviceNames = defineContent('serviceNames')

export const serviceStatus = defineContent('serviceStatus', { methods: { checkedAt: true } })
