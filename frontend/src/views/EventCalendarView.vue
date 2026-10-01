<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import AppIcon from '@/components/AppIcon.vue'
import { getCalendar } from '@/calendar/api.js'
import {
  eventTypes,
  holidayKinds,
  typeLabel,
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
const loading = ref(true)
const error = ref('')
const year = ref(Number(today.slice(0, 4)))
const month = ref(Number(today.slice(5, 7)))
const category = ref('all')
const selectedDate = ref(today)
const selectedEventId = ref(null)
const detailPanel = ref(null)
const yearEvents = computed(() =>
  (calendar.value?.events || [])
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
  return Object.hasOwn(eventTypes, type) || Object.hasOwn(holidayKinds, type)
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
      error.value = 'Không thể tải lịch. Vui lòng thử lại.'
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
        <p class="eyebrow">LỊCH NỘI BỘ</p>
        <h1>Lịch nghỉ & sự kiện</h1>
        <p class="muted">Theo dõi những ngày đặc biệt trong năm, cùng nhau lên kế hoạch.</p>
      </div>
      <RouterLink
        v-if="auth.hasRole(calendarManagementRoles)"
        to="/calendar-events"
        class="button button-primary"
        >Quản lý lịch</RouterLink
      >
      <span v-else class="calendar-readonly">
        <AppIcon name="eye" :size="16" /> Dành cho toàn thể nhân viên
      </span>
    </div>
    <p v-if="loading" role="status" class="panel calendar-loading">Đang tải lịch…</p>
    <div v-else-if="error" class="alert alert-error" role="alert">
      {{ error }} <button class="button button-secondary" @click="load">Thử lại</button>
    </div>
    <template v-else-if="calendar">
      <header class="calendar-banner">
        <div>
          <h2>Lịch nghỉ & sự kiện {{ calendar.year }}</h2>
          <p>{{ yearEvents.length }} kỳ nghỉ & sự kiện · Lịch tham khảo nội bộ</p>
        </div>
        <div class="calendar-year-control">
          <label for="calendar-year">Năm</label>
          <select id="calendar-year" v-model.number="year">
            <option
              v-for="availableYear in calendar.availableYears"
              :key="availableYear"
              :value="availableYear"
            >
              {{ availableYear }}
            </option>
          </select>
        </div>
      </header>
      <div class="calendar-controls">
        <div class="calendar-month-controls">
          <button
            class="icon-button"
            aria-label="Tháng trước"
            :disabled="month === 1"
            @click="changeMonth(month - 1)"
          >
            ‹
          </button>
          <label class="sr-only" for="calendar-month">Tháng</label>
          <select
            id="calendar-month"
            :value="month"
            @change="changeMonth(Number($event.target.value))"
          >
            <option v-for="value in 12" :key="value" :value="value">Tháng {{ value }}</option>
          </select>
          <button
            class="icon-button"
            aria-label="Tháng sau"
            :disabled="month === 12"
            @click="changeMonth(month + 1)"
          >
            ›
          </button>
          <button
            v-if="calendar.availableYears.includes(Number(today.slice(0, 4)))"
            class="button button-secondary"
            @click="goToday"
          >
            Hôm nay
          </button>
        </div>
        <div class="calendar-filter">
          <label for="calendar-category">Loại sự kiện</label>
          <select id="calendar-category" v-model="category">
            <option value="all">Tất cả loại</option>
            <option v-for="(label, value) in eventTypes" :key="value" :value="value">
              {{ label }}
            </option>
          </select>
        </div>
      </div>
      <ul class="calendar-legend" aria-label="Chú thích màu lịch">
        <li v-for="type in legendTypes" :key="type" :class="kind(type)">
          <span class="calendar-dot" aria-hidden="true"></span>{{ typeLabel(type) }}
        </li>
        <li><span class="calendar-weekend-swatch" aria-hidden="true"></span>Cuối tuần</li>
      </ul>
      <div class="calendar-layout">
        <section class="panel calendar-board" aria-labelledby="month-title">
          <div class="calendar-board-heading">
            <h2 id="month-title">
              Tháng {{ month }} <span class="muted">/ {{ year }}</span>
            </h2>
            <span class="small muted">{{ monthEventCount }} kỳ nghỉ & sự kiện</span>
          </div>
          <div class="calendar-weekdays" aria-hidden="true">
            <span v-for="day in ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']" :key="day">{{
              day
            }}</span>
          </div>
          <div class="calendar-days">
            <template v-for="(cell, index) in cells" :key="index">
              <button
                v-if="cell"
                class="calendar-day"
                :class="[
                  cell.entries.length ? kind(cell.entries[0].type) : '',
                  {
                    'has-events': cell.entries.length,
                    'is-weekend': cell.weekend,
                    'is-today': cell.date === today,
                    'is-selected': cell.date === selectedDate,
                  },
                ]"
                :data-date="cell.date"
                :aria-pressed="cell.date === selectedDate"
                :aria-current="cell.date === today ? 'date' : undefined"
                :aria-label="`${displayDate(cell.date)}${cell.weekend ? ', cuối tuần' : ''}${cell.entries.map((entry) => `, ${entry.event.title}, ${typeLabel(entry.type)}`).join('')}`"
                @click="chooseDay(cell.date)"
              >
                <span class="calendar-day-number">{{ cell.day }}</span>
                <span
                  v-for="entry in cell.entries"
                  :key="entry.event.id"
                  class="calendar-day-event"
                  :class="kind(entry.type)"
                  ><span class="calendar-dot" aria-hidden="true"></span
                  ><span class="calendar-day-name"
                    >{{ entry.event.title
                    }}{{ entry.event.status === 'CANCELLED' ? ' · Đã hủy' : '' }}</span
                  ></span
                >
                <span v-if="cell.entries[0]?.event.holidayKind" class="calendar-day-type">{{
                  typeLabel(cell.entries[0].type)
                }}</span>
              </button>
              <div v-else class="calendar-day-empty" aria-hidden="true"></div>
            </template>
          </div>
          <p class="calendar-board-footnote">
            Chọn một ngày để xem chi tiết. Màu sắc thể hiện loại ngày trong dữ liệu lịch.
          </p>
        </section>
        <aside
          ref="detailPanel"
          tabindex="-1"
          class="panel calendar-detail"
          aria-labelledby="day-title"
          aria-live="polite"
        >
          <p class="eyebrow">CHI TIẾT NGÀY</p>
          <h2 id="day-title">{{ displayDate(selectedDate) }}</h2>
          <template v-if="selectedEvent">
            <div v-if="selectedEntries.length > 1" class="calendar-event-switch">
              <button
                v-for="entry in selectedEntries"
                :key="entry.event.id"
                class="button button-secondary"
                :aria-pressed="selectedEvent.id === entry.event.id"
                @click="selectedEventId = entry.event.id"
              >
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
              Đã hủy · Sự kiện không còn hiệu lực.
            </p>
            <p v-if="selectedEvent.holidayKind">
              <span class="calendar-badge" :class="kind(selectedEvent.holidayKind)">{{
                typeLabel(selectedEvent.holidayKind)
              }}</span>
            </p>
            <p v-if="selectedEvent.location">
              <strong>Địa điểm:</strong> {{ selectedEvent.location }}
            </p>
            <p v-if="selectedEvent.description" class="calendar-description">
              {{ selectedEvent.description }}
            </p>
            <p v-else class="muted small">Không có ghi chú bổ sung cho sự kiện này.</p>
          </template>
          <div v-else class="calendar-no-event">
            <AppIcon name="calendar" :size="36" />
            <p>Không có sự kiện phù hợp vào ngày này.</p>
            <span class="small muted">Chọn ngày được đánh dấu hoặc một sự kiện bên dưới.</span>
          </div>
        </aside>
      </div>
      <section class="section-block" aria-labelledby="year-events-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">KẾ HOẠCH CẢ NĂM</p>
            <h2 id="year-events-title">Kỳ nghỉ & sự kiện {{ year }}</h2>
          </div>
          <span class="muted small">{{ visibleEvents.length }} sự kiện</span>
        </div>
        <p v-if="!visibleEvents.length" class="panel calendar-loading" role="status">
          Chưa có sự kiện thuộc loại này trong năm {{ year }}.
        </p>
        <div v-else class="calendar-event-list">
          <button
            v-for="event in visibleEvents"
            :key="event.id"
            class="panel calendar-event-card"
            :class="kind(event.type)"
            :aria-pressed="selectedEvent?.id === event.id"
            @click="chooseEvent(event)"
          >
            <span class="calendar-event-date"
              ><strong>{{ Number(event.startDay.slice(8, 10)) }}</strong
              ><small>THÁNG {{ Number(event.startDay.slice(5, 7)) }}</small></span
            >
            <span
              ><span class="calendar-badge" :class="kind(event.type)">{{
                typeLabel(event.type)
              }}</span
              ><strong class="calendar-event-title"
                >{{ event.title }}{{ event.status === 'CANCELLED' ? ' · Đã hủy' : '' }}</strong
              ><span class="small muted">{{ displayEventRange(event) }}</span></span
            >
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped src="@/assets/styles/event-calendar.css"></style>
