<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { calendarWeekdays } from '@/shared/intl.js'
import { refreshIntervals } from '@/config/runtime.js'

import { dateLimits } from '@/config/validation.js'

import { eventCalendar as copy } from '@/content/calendar.js'
import { common } from '@/content/common.js'

import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import AppIcon from '@/components/AppIcon.vue'
import GoogleMapsLink from '@/components/GoogleMapsLink.vue'
import { getCalendar } from '@/calendar/api.js'
import { leave } from '@/leave/api.js'
import { personalLeaveEvents } from '@/calendar/leave.js'
import { subscribeNotifications } from '@/notifications/stream.js'
import { requestChangedEvent } from '@/notifications/changes.js'
import { leavePeriods, leaveStatuses } from '@/leave/constants.js'
import {
    eventTypes,
    holidayKinds,
    typeLabel as companyTypeLabel,
    calendarManagementRoles,
} from '@/calendar/constants.js'
import {
    calendarDate,
    eventDateRange,
    isoDay,
    monthCells,
    dayEntries,
    displayDate,
    displayEventRange,
} from '@/calendar/utils.js'

const today = calendarDate()
const calendar = ref(null)
const personalLeaves = ref([])
const leaveError = useLocalizedMessage()
const leaveLoading = ref(false)
const visibleTypes = computed(() => ({ ...eventTypes, PERSONAL_LEAVE: copy.myLeave }))
const typeLabel = (type) =>
    type === 'PERSONAL_LEAVE' ? visibleTypes.value.PERSONAL_LEAVE : companyTypeLabel(type)
const loading = ref(true)
const error = useLocalizedMessage()
const year = ref(Number(today.slice(0, 4)))
const availableYears = computed(() =>
    [
        ...new Set([
            ...(calendar.value?.availableYears || []),
            year.value,
            ...[year.value - 1, year.value + 1].filter(
                (value) => value >= dateLimits.minYear && value <= dateLimits.calendarMaxYear,
            ),
        ]),
    ].sort((a, b) => a - b),
)
const month = ref(Number(today.slice(5, 7)))
const category = ref('all')
const selectedDate = ref(today)
const selectedEventId = ref(null)
const detailPanel = ref(null)
const yearEvents = computed(() =>
    [...(calendar.value?.events || []), ...personalLeaves.value]
        .map((event) => ({ ...event, ...eventDateRange(event) }))
        .filter(
            (event) =>
                event.startDay <= isoDay(year.value, 12, 31) && event.endDay >= isoDay(year.value, 1, 1),
        ),
)
const visibleEvents = computed(() =>
    yearEvents.value.filter((event) => category.value === 'all' || event.type === category.value),
)
const cells = computed(() =>
    monthCells(year.value, month.value).map(
        (cell) => cell && { ...cell, entries: dayEntries(visibleEvents.value, cell.date) },
    ),
)
const selectedEntries = computed(() => dayEntries(visibleEvents.value, selectedDate.value))
const selectedEvent = computed(
    () =>
        selectedEntries.value.find((entry) => entry.event.id === selectedEventId.value)?.event ||
        selectedEntries.value[0]?.event,
)
const monthEventCount = computed(() => {
    const lastDay = new Date(Date.UTC(year.value, month.value, 0)).getUTCDate()
    return visibleEvents.value.filter(
        (event) =>
            event.startDay <= isoDay(year.value, month.value, lastDay) &&
            event.endDay >= isoDay(year.value, month.value, 1),
    ).length
})
const legendTypes = computed(() => [
    ...new Set(yearEvents.value.map((event) => event.holidayKind || event.type)),
])
function kind(type) {
    return Object.hasOwn(visibleTypes.value, type) || Object.hasOwn(holidayKinds, type)
        ? `kind-${type}`
        : 'kind-OTHER'
}
function chooseEvent(event) {
    const date = event.startDay < isoDay(year.value, 1, 1) ? isoDay(year.value, 1, 1) : event.startDay
    month.value = Number(date.slice(5, 7))
    selectedDate.value = date
    selectedEventId.value = event.id
    nextTick(() => detailPanel.value?.focus())
}
function chooseDay(date) {
    selectedDate.value = date
    selectedEventId.value = null
}
function changeMonth(value) {
    month.value = value
    chooseDay(isoDay(year.value, value, 1))
}
function goToday() {
    year.value = Number(today.slice(0, 4))
    month.value = Number(today.slice(5, 7))
    chooseDay(today)
}
watch(year, () => chooseDay(isoDay(year.value, month.value, 1)), { flush: 'sync' })
watch(category, () => {
    selectedEventId.value = null
})

let loadId = 0
let leaveLoadId = 0
let stopped = false
let leaveTimer
let stopNotifications
async function loadPersonalLeaves() {
    const ticket = ++leaveLoadId
    const owner = auth.state.user?.id
    const requestedYear = year.value
    leaveLoading.value = true
    try {
        const data = await leave.calendar(requestedYear)
        if (!Array.isArray(data)) throw new Error('Invalid leave calendar')
        if (!stopped && ticket === leaveLoadId && owner === auth.state.user?.id) {
            personalLeaves.value = personalLeaveEvents(data)
            leaveError.value = ''
        }
    } catch {
        if (!stopped && ticket === leaveLoadId) {
            leaveError.value = () => copy.personalLeaveFailed
        }
    } finally {
        if (!stopped && ticket === leaveLoadId) leaveLoading.value = false
    }
}
async function pollPersonalLeaves() {
    await loadPersonalLeaves()
    if (!stopped) leaveTimer = window.setTimeout(pollPersonalLeaves, refreshIntervals.personalLeave)
}
watch(
    year,
    () => {
        personalLeaves.value = []
        leaveError.value = ''
        loadPersonalLeaves()
    },
    { immediate: true, flush: 'sync' },
)
watch(
    () => auth.state.user?.id,
    () => {
        personalLeaves.value = []
        leaveLoadId++
        if (auth.state.user) loadPersonalLeaves()
    },
    { flush: 'sync' },
)
onMounted(() => {
    stopNotifications = subscribeNotifications(loadPersonalLeaves)
    window.addEventListener(requestChangedEvent, loadPersonalLeaves)
    leaveTimer = window.setTimeout(pollPersonalLeaves, refreshIntervals.personalLeave)
})
onBeforeUnmount(() => {
    stopped = true
    loadId++
    leaveLoadId++
    window.clearTimeout(leaveTimer)
    stopNotifications?.()
    window.removeEventListener(requestChangedEvent, loadPersonalLeaves)
})
async function load() {
    const currentLoad = ++loadId
    loading.value = true
    error.value = ''
    try {
        const data = await getCalendar(year.value)
        if (currentLoad === loadId) calendar.value = data
    } catch (err) {
        if (currentLoad === loadId) {
            console.error(err)
            error.value = () => copy.loadFailed
        }
    } finally {
        if (currentLoad === loadId) loading.value = false
    }
}
watch(year, load, { immediate: true })
</script>

<template>
    <div class="event-calendar">
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ copy.title }}</h1>
                <p class="muted">{{ copy.description }}</p>
            </div>
            <RouterLink v-if="auth.hasRole(calendarManagementRoles)" to="/calendar-events"
                class="button button-primary">{{ copy.manage }}</RouterLink>
            <span v-else class="calendar-readonly">
                <AppIcon name="eye" :size="16" /> {{ copy.privacyHint }}
            </span>
        </div>
        <p v-if="loading" role="status" class="panel calendar-loading">{{ copy.loading }}</p>
        <div v-else-if="error" class="alert alert-error" role="alert">
            {{ error }} <button class="button button-secondary" @click="load">{{ common.retry }}</button>
        </div>
        <template v-else-if="calendar">
            <div v-if="leaveError" class="alert alert-error" role="alert">
                {{ leaveError }}
                <button class="button button-secondary" :disabled="leaveLoading" @click="loadPersonalLeaves">
                    {{ copy.reloadLeave }}
                </button>
            </div>
            <header class="calendar-banner">
                <div>
                    <h2>{{ copy.yearTitle({ year: calendar.year }) }}</h2>
                    <p>{{ copy.yearCount({ count: yearEvents.length }) }}</p>
                </div>
                <div class="calendar-year-control">
                    <label for="calendar-year">{{ common.year }}</label>
                    <select id="calendar-year" v-model.number="year">
                        <option v-for="availableYear in availableYears" :key="availableYear" :value="availableYear">
                            {{ availableYear }}
                        </option>
                    </select>
                </div>
            </header>
            <div class="calendar-controls">
                <div class="calendar-month-controls">
                    <button class="icon-button" :aria-label="copy.previousMonth" :disabled="month === 1"
                        @click="changeMonth(month - 1)">
                        ‹
                    </button>
                    <label class="sr-only" for="calendar-month">{{ common.month }}</label>
                    <select id="calendar-month" :value="month" @change="changeMonth(Number($event.target.value))">
                        <option v-for="value in 12" :key="value" :value="value">
                            {{ copy.monthLabel({ month: value }) }}
                        </option>
                    </select>
                    <button class="icon-button" :aria-label="copy.nextMonth" :disabled="month === 12"
                        @click="changeMonth(month + 1)">
                        ›
                    </button>
                    <button v-if="calendar.availableYears.includes(Number(today.slice(0, 4)))"
                        class="button button-secondary" @click="goToday">
                        {{ copy.today }}
                    </button>
                </div>
                <div class="calendar-filter">
                    <label for="calendar-category">{{ copy.type }}</label>
                    <select id="calendar-category" v-model="category">
                        <option value="all">{{ copy.allTypes }}</option>
                        <option v-for="(label, value) in visibleTypes" :key="value" :value="value">
                            {{ label }}
                        </option>
                    </select>
                </div>
            </div>
            <ul class="calendar-legend" :aria-label="copy.legend">
                <li v-for="type in legendTypes" :key="type" :class="kind(type)">
                    <span class="calendar-dot" aria-hidden="true"></span>{{ typeLabel(type) }}
                </li>
                <li><span class="calendar-weekend-swatch" aria-hidden="true"></span>{{ copy.weekend }}</li>
            </ul>
            <div class="calendar-layout">
                <section class="panel calendar-board" aria-labelledby="month-title">
                    <div class="calendar-board-heading">
                        <h2 id="month-title">
                            {{ copy.monthTitle({ month: month }) }} <span class="muted">/ {{ year }}</span>
                        </h2>
                        <span class="small muted">{{
                            copy.monthCount({ monthEventCount: monthEventCount })
                            }}</span>
                    </div>
                    <div class="calendar-weekdays" aria-hidden="true">
                        <span v-for="day in calendarWeekdays" :key="day">{{ day }}</span>
                    </div>
                    <div class="calendar-days">
                        <template v-for="(cell, index) in cells" :key="index">
                            <button v-if="cell" class="calendar-day" :class="[
                                cell.entries.length ? kind(cell.entries[0].type) : '',
                                {
                                    'has-events': cell.entries.length,
                                    'is-weekend': cell.weekend,
                                    'is-today': cell.date === today,
                                    'is-selected': cell.date === selectedDate,
                                },
                            ]" :data-date="cell.date" :aria-pressed="cell.date === selectedDate"
                                :aria-current="cell.date === today ? 'date' : undefined"
                                :aria-label="`${displayDate(cell.date)}${cell.weekend ? copy.weekendSuffix : ''}${cell.entries.map((entry) => `, ${entry.event.title}, ${typeLabel(entry.type)}`).join('')}`"
                                @click="chooseDay(cell.date)">
                                <span class="calendar-day-number">{{ cell.day }}</span>
                                <span v-for="entry in cell.entries" :key="entry.event.id" class="calendar-day-event"
                                    :class="kind(entry.type)"><span class="calendar-dot" aria-hidden="true"></span><span
                                        class="calendar-day-name">{{ entry.event.title
                                        }}{{ entry.event.status === 'CANCELLED' ? copy.cancelledSuffix : ''
                                        }}</span></span>
                                <span v-if="cell.entries[0]?.event.holidayKind" class="calendar-day-type">{{
                                    typeLabel(cell.entries[0].type)
                                    }}</span>
                            </button>
                            <div v-else class="calendar-day-empty" aria-hidden="true"></div>
                        </template>
                    </div>
                    <p class="calendar-board-footnote">{{ copy.calendarHint }}</p>
                </section>
                <aside ref="detailPanel" tabindex="-1" class="panel calendar-detail" aria-labelledby="day-title"
                    aria-live="polite">
                    <p class="eyebrow">{{ copy.dayDetail }}</p>
                    <h2 id="day-title">{{ displayDate(selectedDate) }}</h2>
                    <template v-if="selectedEvent">
                        <div v-if="selectedEntries.length > 1" class="calendar-event-switch">
                            <button v-for="entry in selectedEntries" :key="entry.event.id"
                                class="button button-secondary" :aria-pressed="selectedEvent.id === entry.event.id"
                                @click="selectedEventId = entry.event.id">
                                {{ entry.event.title }}
                            </button>
                        </div>
                        <span class="calendar-badge" :class="kind(selectedEvent.type)">{{
                            typeLabel(selectedEvent.type)
                            }}</span>
                        <h3>{{ selectedEvent.title }}</h3>

                        <p class="calendar-detail-range">
                            <AppIcon name="calendar" :size="17" />{{ displayEventRange(selectedEvent) }}
                        </p>
                        <p v-if="selectedEvent.status === 'CANCELLED'" class="alert alert-error">
                            {{ copy.cancelledHint }}
                        </p>
                        <p v-if="selectedEvent.type === 'PERSONAL_LEAVE'">
                            {{ leaveStatuses[selectedEvent.status] }} · {{ leavePeriods[selectedEvent.period] }}
                            <RouterLink :to="`/leave/requests/${selectedEvent.requestId}`"
                                class="button button-secondary">{{ copy.viewLeave }}</RouterLink>
                        </p>
                        <p v-if="selectedEvent.holidayKind">
                            <span class="calendar-badge" :class="kind(selectedEvent.holidayKind)">{{
                                typeLabel(selectedEvent.holidayKind)
                                }}</span>
                        </p>
                        <p v-if="selectedEvent.location">
                            <strong>{{ copy.location }}</strong> {{ selectedEvent.location }}
                        </p>
                        <GoogleMapsLink :location="selectedEvent.location" />
                        <p v-if="selectedEvent.description" class="calendar-description">
                            {{ selectedEvent.description }}
                        </p>
                        <p v-else class="muted small">{{ copy.noEventNote }}</p>
                    </template>
                    <div v-else class="calendar-no-event">
                        <AppIcon name="calendar" :size="36" />
                        <p>{{ copy.emptyDay }}</p>
                        <span class="small muted">{{ copy.selectDayHint }}</span>
                    </div>
                </aside>
            </div>
            <section class="section-block" aria-labelledby="year-events-title">
                <div class="section-heading">
                    <div>
                        <p class="eyebrow">{{ copy.yearPlan }}</p>
                        <h2 id="year-events-title">{{ copy.yearPlanTitle({ year: year }) }}</h2>
                    </div>
                    <span class="muted small">{{ copy.eventCount({ count: visibleEvents.length }) }}</span>
                </div>
                <p v-if="!visibleEvents.length" class="panel calendar-loading" role="status">
                    {{ copy.emptyYear({ year: year }) }}
                </p>
                <div v-else class="calendar-event-list">
                    <button v-for="event in visibleEvents" :key="event.id" class="panel calendar-event-card"
                        :class="kind(event.type)" :aria-pressed="selectedEvent?.id === event.id"
                        @click="chooseEvent(event)">
                        <span class="calendar-event-date"><strong>{{ Number(event.startDay.slice(8, 10))
                                }}</strong><small>{{
                                    copy.monthHeading({ month: Number(event.startDay.slice(5, 7)) })
                                }}</small></span>
                        <span><span class="calendar-badge" :class="kind(event.type)">{{
                            typeLabel(event.type)
                                }}</span><strong class="calendar-event-title">{{ event.title
                                }}{{ event.status === 'CANCELLED' ? copy.cancelledSuffix : '' }}</strong><span
                                class="small muted">{{ displayEventRange(event) }}</span></span>
                    </button>
                </div>
            </section>
        </template>
    </div>
</template>

<style scoped src="@/assets/styles/event-calendar.css"></style>
