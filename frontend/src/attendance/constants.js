import { computed } from 'vue'
import { defineContent } from '../i18n/content.js'
export const attendanceRoles = ['HR', 'ADMIN']
export const statuses = defineContent('attendanceStatuses')
const weekdayLabels = defineContent('weekdays')
export const weekdays = computed(() => Object.entries(weekdayLabels))
