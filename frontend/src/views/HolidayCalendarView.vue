<script setup>
import { computed, nextTick, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import {
  calendarYear, companyName, weekdays, eventTypes, holidayGroups, salaryDates,
  pendingActivities, formatCalendarDate, formatPeriod, dayEvents, monthDays,
} from '@/data/holiday-calendar.js'

const months = Array.from({ length: 12 }, (_, index) => ({ number: index + 1, days: monthDays(index + 1) }))
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const selectedDate = ref(today.startsWith(`${calendarYear}-`) ? today : `${calendarYear}-01-01`)
const region = ref('all')
const selectedEvents = computed(() => dayEvents(selectedDate.value))
const selectedWeekday = computed(() => new Intl.DateTimeFormat('vi-VN', { weekday: 'long', timeZone: 'UTC' }).format(new Date(`${selectedDate.value}T00:00:00Z`)))
const filteredGroups = computed(() => holidayGroups.filter(group => region.value === 'all' || group.country === region.value))
const currentMonth = Number(today.slice(5, 7))
const selectedNotes = computed(() => [...new Set(selectedEvents.value.flatMap(event => event.notes))])
const selectedWeekend = computed(() => [0, 6].includes(new Date(`${selectedDate.value}T00:00:00Z`).getUTCDay()))

function dayLabel(day) {
  return [formatCalendarDate(day.date), ...day.events.map(event => event.label)].join(' · ')
}
async function selectFromList(date) {
  selectedDate.value = date
  await nextTick()
  document.getElementById(`calendar-day-${date}`)?.focus({ preventScroll: true })
  document.getElementById(`calendar-day-${date}`)?.scrollIntoView({ behavior: 'auto', block: 'nearest' })
}
function goToToday() { void selectFromList(today) }
</script>

<template>
  <div class="holiday-page">
    <div class="page-heading">
      <div><p class="eyebrow">LỊCH CÔNG TY</p><h1>Lịch nghỉ</h1><p class="muted">Theo dõi ngày nghỉ, ngày lương và các mốc trong năm.</p></div>
      <div class="calendar-heading-actions"><button v-if="today.startsWith(`${calendarYear}-`)" class="button button-secondary" @click="goToToday">Hôm nay</button><span class="calendar-year"><AppIcon name="calendar" :size="18" />{{ calendarYear }}</span></div>
    </div>

    <section class="calendar-intro" aria-label="Thông tin lịch">
      <div class="calendar-intro-copy"><span class="calendar-company">{{ companyName }}</span><h2>Một năm để chủ động sắp xếp.</h2><p>Lịch nghỉ Việt Nam và các ngày lễ Nhật Bản theo thông báo công ty năm 2026.</p></div>
      <div class="calendar-year-art" aria-hidden="true">20<span>26</span></div>
    </section>

    <div class="calendar-stats">
      <div class="panel calendar-stat"><span class="calendar-stat-icon blue"><AppIcon name="calendar" /></span><div><strong>05 <span>kỳ nghỉ Việt Nam</span></strong><p>Bao gồm ngày phép & nghỉ thêm dự kiến</p></div></div>
      <div class="panel calendar-stat"><span class="calendar-stat-icon calendar-pink"><AppIcon name="globe" /></span><div><strong>03 <span>ngày lễ Nhật Bản</span></strong><p>Hiển thị riêng theo lịch được cung cấp</p></div></div>
      <div class="panel calendar-stat"><span class="calendar-stat-icon green"><AppIcon name="wallet" /></span><div><strong>{{ salaryDates.length }} <span>ngày lương</span></strong><p>Được đánh dấu trên lịch từng tháng</p></div></div>
    </div>

    <div class="holiday-layout">
      <section class="panel annual-calendar" aria-labelledby="year-calendar-title">
        <div class="annual-calendar-header"><div><h2 id="year-calendar-title">Lịch năm {{ calendarYear }}</h2><p>Chọn một ngày để xem thông tin chi tiết.</p></div><span class="calendar-month-count">12 tháng</span></div>
        <div class="calendar-legend" aria-label="Chú giải"><span v-for="(event, type) in eventTypes" :key="type"><i class="legend-dot" :class="`event-${type}`"></i>{{ event.label }}</span><span><i class="legend-dot weekend-saturday"></i>Thứ Bảy</span><span><i class="legend-dot weekend-sunday"></i>Chủ nhật</span></div>
        <div class="months-grid">
          <section v-for="month in months" :key="month.number" class="calendar-month" :aria-label="`Tháng ${month.number} năm ${calendarYear}`">
            <div class="month-title"><h3>Tháng {{ String(month.number).padStart(2, '0') }}</h3><span v-if="today.startsWith(`${calendarYear}-`) && currentMonth === month.number" class="current-month-tag">Tháng này</span></div>
            <div class="month-weekdays" aria-hidden="true"><span v-for="(day, index) in weekdays" :key="day" :class="{ saturday: index === 5, sunday: index === 6 }">{{ day }}</span></div>
            <div class="month-dates">
              <template v-for="(day, index) in month.days" :key="index">
                <button v-if="day" :id="`calendar-day-${day.date}`" type="button" class="calendar-day" :class="{
                  saturday: day.weekday === 5, sunday: day.weekday === 6,
                  'is-selected': day.date === selectedDate, 'is-today': day.date === today,
                  'has-holiday': day.events.some(event => event.type === 'holiday'),
                  'has-annual': day.events.some(event => event.type === 'annual'),
                  'has-extra': day.events.some(event => event.type === 'extra'),
                  'has-salary': day.events.some(event => event.type === 'salary'),
                  'japan-holiday': day.events.some(event => event.country === 'JP'),
                }" :aria-label="dayLabel(day)" :aria-pressed="day.date === selectedDate" :aria-current="day.date === today ? 'date' : undefined" :title="dayLabel(day)" @click="selectedDate = day.date">
                  {{ day.number }}<span v-if="day.events.length" class="day-markers" aria-hidden="true"><i v-for="(event, eventIndex) in day.events" :key="eventIndex" :class="`event-${event.type}`"></i></span>
                </button>
                <span v-else class="calendar-day-empty" aria-hidden="true"></span>
              </template>
            </div>
          </section>
        </div>
        <div class="calendar-bottom-note"><AppIcon name="info" :size="16" /><p>Các ngày lễ Nhật Bản có viền nét đứt. Lịch này không tự động trừ phép hoặc ghi nhận chấm công.</p></div>
      </section>

      <aside class="holiday-aside" aria-label="Chi tiết lịch nghỉ">
        <section class="panel selected-day-panel" aria-live="polite" aria-atomic="true">
          <p class="eyebrow">NGÀY ĐANG CHỌN</p><div class="selected-day-heading"><div><h2>{{ formatCalendarDate(selectedDate) }}</h2><span>{{ selectedWeekday }}</span></div><span class="calendar-detail-icon"><AppIcon name="calendar" :size="25" /></span></div>
          <div v-if="selectedEvents.length" class="selected-events"><div v-for="(event, index) in selectedEvents" :key="index" class="selected-event"><span class="event-pill" :class="`pill-${event.type}`">{{ eventTypes[event.type].label }}</span><strong>{{ event.label }}</strong><span v-if="event.country === 'JP'" class="country-caption">Lịch Nhật Bản</span></div><p v-for="note in selectedNotes" :key="note" class="selected-note">{{ note }}</p></div>
          <p v-else class="selected-empty">{{ selectedWeekend ? 'Ngày cuối tuần. Không có sự kiện khác được đánh dấu.' : 'Không có ngày nghỉ hoặc sự kiện được đánh dấu trong lịch cung cấp.' }}</p>
        </section>

        <section class="panel holiday-list-panel" aria-labelledby="holiday-list-title"><div class="holiday-list-header"><h2 id="holiday-list-title">Các kỳ nghỉ trong năm</h2><span>{{ filteredGroups.length }}</span></div>
          <div class="region-filter" role="group" aria-label="Lọc lịch theo quốc gia"><button v-for="filter in [{ value: 'all', label: 'Tất cả' }, { value: 'VN', label: 'Việt Nam' }, { value: 'JP', label: 'Nhật Bản' }]" :key="filter.value" :aria-pressed="region === filter.value" :class="{ active: region === filter.value }" @click="region = filter.value">{{ filter.label }}</button></div>
          <div class="holiday-list"><article v-for="group in filteredGroups" :key="group.id" class="holiday-list-item"><button class="holiday-item-link" @click="selectFromList(group.start)"><span class="holiday-date-tile"><strong>{{ group.start.slice(8) }}</strong><small>TH{{ group.start.slice(5, 7) }}</small></span><span class="holiday-item-title"><span class="country-label" :class="{ japan: group.country === 'JP' }">{{ group.country === 'JP' ? 'NHẬT BẢN' : 'VIỆT NAM' }}</span><strong>{{ group.title }}</strong><span class="holiday-english">{{ group.english }}</span></span><AppIcon name="arrow" :size="15" /></button><p class="holiday-period">{{ formatPeriod(group.start, group.end) }}</p><ul v-if="group.notes.length" class="holiday-notes"><li v-for="note in group.notes" :key="note">{{ note }}</li></ul></article></div>
        </section>
        <section class="panel company-activities"><h2>Hoạt động công ty</h2><div v-for="activity in pendingActivities" :key="activity.type"><i class="legend-dot" :class="`event-${activity.type}`"></i><span>{{ activity.label }}<small>Chờ xác nhận ngày từ lịch gốc</small></span></div></section>
      </aside>
    </div>
    <p class="calendar-source">Nguồn: Calendar 2026 · {{ companyName }}. Các ngày nghỉ thêm dự kiến có thể thay đổi theo thông báo công ty.</p>
  </div>
</template>

<style scoped>
.calendar-heading-actions { display: flex; align-items: center; gap: 10px; }
.calendar-year { display: inline-flex; align-items: center; gap: 10px; border: 1px solid #dbe5e1; background: white; color: #315d52; border-radius: 8px; padding: 9px 16px; font-weight: 650; font-size: 16px; }
.calendar-intro { position: relative; overflow: hidden; display: flex; align-items: center; justify-content: space-between; padding: 28px 32px; background: #163b37; border-radius: 12px; color: white; margin-bottom: 20px; min-height: 148px; }
.calendar-intro-copy { z-index: 1; }
.calendar-company { color: #9cc9ba; font-size: 10px; letter-spacing: 1.5px; }
.calendar-intro h2 { font-size: 25px; font-weight: 550; margin: 10px 0; letter-spacing: -.5px; }
.calendar-intro p { color: #bad1c9; font-size: 12px; margin: 0; max-width: 460px; }
.calendar-year-art { font-size: 88px; font-weight: 750; line-height: .85; letter-spacing: -7px; color: #ffffff18; transform: rotate(-8deg); padding: 5px 15px; user-select: none; }
.calendar-year-art span { color: #8ec4ad66; }
.calendar-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
.calendar-stat { display: flex; align-items: center; gap: 13px; padding: 19px; }
.calendar-stat-icon { display: inline-flex; align-items: center; justify-content: center; border-radius: 10px; width: 40px; height: 40px; flex-shrink: 0; }
.calendar-pink { color: #b66c80; background: #fceff3; }
.calendar-stat strong { font-size: 23px; font-weight: 650; display: flex; align-items: baseline; gap: 8px; }
.calendar-stat strong span { font-size: 11px; font-weight: 500; color: #5e6f7e; }
.calendar-stat p { font-size: 10px; color: #7e8b98; margin: 1px 0 0; }
.holiday-layout { display: grid; grid-template-columns: minmax(0, 1fr) 312px; gap: 22px; align-items: start; }
.annual-calendar { overflow: hidden; }
.annual-calendar-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 23px 24px 17px; }
.annual-calendar-header h2 { margin: 0 0 6px; font-size: 17px; }
.annual-calendar-header p { margin: 0; font-size: 11px; color: #7c8998; }
.calendar-month-count { font-size: 10px; color: #6b8090; background: #f2f5f8; border-radius: 5px; padding: 5px 9px; white-space: nowrap; }
.calendar-legend { display: flex; flex-wrap: wrap; gap: 10px 15px; padding: 15px 24px; border-top: 1px solid #edf0f4; border-bottom: 1px solid #edf0f4; background: #fcfdfd; }
.calendar-legend > span { display: inline-flex; align-items: center; gap: 6px; color: #647484; font-size: 10px; }
.legend-dot { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0; display: inline-block; }
.event-holiday { background: #5e83dc; }
.event-annual { background: #9c76cc; }
.event-extra { background: #db9363; }
.event-salary { background: #cf5269; }
.event-party { background: #d9aa36; }
.event-travel { background: #59a67c; }
.weekend-saturday { background: #b6e5e2; }
.weekend-sunday { background: #edc4df; }
.months-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); padding: 9px; }
.calendar-month { padding: 17px 12px; min-width: 0; border-bottom: 1px solid #f0f2f5; }
.month-title { display: flex; align-items: center; gap: 5px; justify-content: space-between; margin-bottom: 14px; min-height: 20px; }
.month-title h3 { font-size: 12px; font-weight: 650; margin: 0; }
.current-month-tag { font-size: 8px; color: #397f6a; background: #eef8f2; padding: 2px 5px; border-radius: 4px; }
.month-weekdays, .month-dates { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; }
.month-weekdays { margin-bottom: 6px; }
.month-weekdays span { text-align: center; font-size: 9px; color: #8b96a3; padding: 3px 0; }
.month-weekdays .saturday { color: #3e928d; }
.month-weekdays .sunday { color: #b4729a; }
.calendar-day, .calendar-day-empty { position: relative; min-width: 0; height: 28px; padding: 0; border: 1px solid transparent; border-radius: 5px; font-size: 10px; line-height: 1; background: white; color: #576678; }
.calendar-day.saturday { color: #34827e; background: #ecf8f6; }
.calendar-day.sunday { color: #b66b94; background: #fcf0f7; }
.calendar-day.has-holiday { color: #3d65b9; background: #eaf0fc; font-weight: 650; }
.calendar-day.has-annual { color: #8357b1; background: #f2ebfa; font-weight: 650; }
.calendar-day.has-extra { color: #b66d39; background: #fff1e5; font-weight: 650; }
.calendar-day.has-salary { color: #c84059; font-weight: 700; }
.calendar-day.japan-holiday { border: 1px dashed #9bb1df; }
.calendar-day.is-today { box-shadow: inset 0 0 0 1px #17796c; }
.calendar-day.is-selected { background: #17796c; color: white; border-color: #17796c; }
.calendar-day:hover { box-shadow: inset 0 0 0 1px #83b8aa; }
.calendar-day:focus-visible { outline: 2px solid #17796c; outline-offset: 1px; z-index: 1; }
.day-markers { position: absolute; bottom: 3px; left: 0; right: 0; display: flex; justify-content: center; gap: 2px; }
.day-markers i { width: 3px; height: 3px; border-radius: 50%; }
.is-selected .day-markers i { background: white; }
.calendar-bottom-note { display: flex; align-items: flex-start; gap: 9px; padding: 16px 24px; background: #fafcfd; color: #7b8998; }
.calendar-bottom-note svg { flex-shrink: 0; margin-top: 2px; }
.calendar-bottom-note p { font-size: 10px; line-height: 1.8; margin: 0; }
.holiday-aside { display: flex; flex-direction: column; gap: 18px; }
.selected-day-panel { padding: 22px; border-top: 3px solid #17796c; }
.selected-day-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 13px; }
.selected-day-heading h2 { font-size: 22px; margin: 0; letter-spacing: -.4px; }
.selected-day-heading span { color: #86929f; font-size: 11px; }
.calendar-detail-icon { background: #edf5f1; color: #5d9b82 !important; padding: 11px; border-radius: 10px; display: flex; }
.selected-empty { margin: 0; color: #7f8c99; font-size: 11px; line-height: 1.8; }
.selected-event { border-top: 1px solid #edf0f3; padding-top: 14px; margin-top: 13px; }
.selected-event > strong { display: block; margin-top: 8px; font-size: 12px; font-weight: 600; }
.event-pill { display: inline-block; font-size: 9px; border-radius: 4px; padding: 3px 7px; }
.pill-holiday { background: #ebf1ff; color: #4a70bf; }
.pill-annual { background: #f3ecfb; color: #8259b5; }
.pill-extra { background: #fff0e6; color: #b57141; }
.pill-salary { background: #fff0f3; color: #c2546a; }
.selected-note { margin: 10px 0 0; color: #7f8995; font-size: 11px; line-height: 1.8; }
.country-caption { font-size: 10px; color: #bd748b; }
.holiday-list-panel { padding: 20px; }
.holiday-list-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 15px; }
.holiday-list-header h2 { font-size: 14px; margin: 0; }
.holiday-list-header > span { font-size: 10px; color: #75849a; background: #f1f4f8; padding: 1px 7px; border-radius: 5px; }
.region-filter { display: flex; padding: 3px; background: #f2f5f7; border-radius: 6px; gap: 3px; }
.region-filter button { flex: 1; border: 0; background: transparent; padding: 6px 4px; border-radius: 5px; font-size: 10px; color: #7a8997; }
.region-filter button.active { background: white; color: #276f5d; box-shadow: 0 1px 4px #24394c0a; font-weight: 600; }
.holiday-list-item { padding: 19px 0; border-bottom: 1px solid #eef1f4; }
.holiday-list-item:last-child { border-bottom: 0; padding-bottom: 0; }
.holiday-item-link { display: flex; align-items: center; gap: 10px; padding: 0; background: transparent; border: 0; text-align: left; width: 100%; color: inherit; }
.holiday-item-link > svg { margin-left: auto; color: #a2aeb9; }
.holiday-date-tile { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 37px; height: 43px; background: #f1f5f8; border: 1px solid #e9eef2; border-radius: 6px; flex-shrink: 0; }
.holiday-date-tile strong { font-size: 17px; font-weight: 600; line-height: 1.2; }
.holiday-date-tile small { color: #8192a3; font-size: 8px; }
.holiday-item-title { display: flex; flex-direction: column; gap: 3px; }
.country-label { color: #4b9176; font-size: 8px; letter-spacing: .8px; }
.country-label.japan { color: #be7891; }
.holiday-item-title > strong { font-size: 11px; font-weight: 600; line-height: 1.5; }
.holiday-english { font-size: 9px; color: #8e9aa6; }
.holiday-period { margin: 12px 0 0; font-size: 10px; color: #688196; }
.holiday-notes { margin: 10px 0 0; padding-left: 14px; color: #8793a0; font-size: 10px; line-height: 1.8; }
.holiday-notes li + li { margin-top: 5px; }
.company-activities { padding: 20px; }
.company-activities h2 { font-size: 14px; margin-bottom: 17px; }
.company-activities > div { display: flex; gap: 9px; align-items: baseline; margin-top: 14px; font-size: 11px; color: #586e7e; }
.company-activities small { display: block; font-size: 10px; color: #8a97a3; margin-top: 4px; }
.calendar-source { font-size: 10px; color: #8b97a5; margin: 20px 0 0; }
@media (min-width: 1600px) { .calendar-day, .calendar-day-empty { height: 32px; } }
@media (max-width: 1200px) { .holiday-layout { grid-template-columns: minmax(0, 1fr) 280px; gap: 16px; } .months-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .calendar-stat { padding: 15px 12px; gap: 9px; } .calendar-stat strong { font-size: 20px; } .calendar-stat strong span { font-size: 10px; } .calendar-year-art { font-size: 65px; } }
@media (max-width: 1000px) { .holiday-layout { grid-template-columns: 1fr; } .months-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .holiday-aside { display: grid; grid-template-columns: 1fr 1fr; align-items: start; } .holiday-list-panel { grid-column: 2; grid-row: span 2; } .company-activities { grid-column: 1; } .calendar-stats { gap: 10px; } .calendar-stat-icon { display: none; } }
@media (max-width: 760px) { .calendar-intro { padding: 24px; } .calendar-intro h2 { font-size: 21px; } .calendar-year-art { display: none; } .calendar-stat strong { flex-direction: column; gap: 1px; } .calendar-stat p { display: none; } .calendar-stat strong span { font-size: 9px; } .months-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .calendar-heading-actions .button { display: none; } .annual-calendar-header { padding: 20px; } .calendar-legend { padding: 14px 20px; } .holiday-aside { display: flex; } .selected-day-panel { order: -1; } }
@media (max-width: 420px) { .months-grid { grid-template-columns: 1fr; padding: 10px 16px; } .calendar-month { padding: 16px 4px; } .calendar-day, .calendar-day-empty { height: 34px; font-size: 12px; } .month-title h3 { font-size: 14px; } .month-weekdays span { font-size: 10px; } .calendar-year { padding: 8px 12px; } }
</style>
