<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { leave } from '@/leave/api.js'
import { dateLabel, leaveError, leavePeriods, timeLabel } from '@/leave/helpers.js'

const props = defineProps({
  request: { type: Object, required: true },
  own: Boolean,
  reviewer: Boolean,
})
const emit = defineEmits(['updated'])
const entries = ref([])
const loading = ref(true)
const saving = ref(false)
const stale = ref(false)
const error = ref('')
const note = ref('')
const form = reactive({
  cancelAll: true,
  startDate: props.request.startDate,
  endDate: props.request.endDate,
  period: props.request.period || 'FULL_DAY',
  reason: '',
})
const pending = computed(() => entries.value.find((entry) => entry.status === 'PENDING'))
const statuses = {
  PENDING: 'Chờ HR xác nhận',
  APPROVED: 'Đã duyệt điều chỉnh',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã rút yêu cầu',
}
let generation = 0
onBeforeUnmount(() => generation++)
watch(
  () => [form.period, form.startDate],
  () => {
    if (form.period !== 'FULL_DAY') form.endDate = form.startDate
  },
)
watch(
  () => props.request.version,
  async () => {
    const current = ++generation
    loading.value = true
    try {
      const data = await leave.adjustments(props.request.id)
      if (current === generation) entries.value = data
    } catch (cause) {
      if (current === generation) {
        error.value = leaveError(cause)
        stale.value = true
      }
    } finally {
      if (current === generation) loading.value = false
    }
  },
  { immediate: true },
)
async function save(action) {
  if (saving.value || loading.value || stale.value) return
  error.value = ''
  if (action === 'submit' && !form.reason.trim()) {
    error.value = 'Vui lòng nhập lý do điều chỉnh.'
    return
  }
  if (action === 'reject' && !note.value.trim()) {
    error.value = 'Vui lòng nhập lý do từ chối.'
    return
  }
  saving.value = true
  const current = generation
  try {
    if (action === 'submit') {
      await leave.adjust(props.request.id, props.request.version, {
        cancelAll: form.cancelAll,
        reason: form.reason.trim(),
        startDate: form.cancelAll ? null : form.startDate,
        endDate: form.cancelAll ? null : form.endDate,
        period: form.cancelAll ? null : form.period,
      })
    } else {
      await leave.decideAdjustment(
        props.request.id,
        pending.value.id,
        action,
        props.request.version,
        note.value.trim(),
      )
    }
    if (current === generation) emit('updated')
  } catch (cause) {
    if (current !== generation) return
    error.value = leaveError(cause)
    if ([0, 409, 412, 428].includes(cause.status) || cause.status >= 500) stale.value = true
  } finally {
    if (current === generation) saving.value = false
  }
}
</script>

<template>
  <section class="panel create-form leave-section">
    <h2>Điều chỉnh phép đã duyệt</h2>
    <p class="muted">
      Nếu không nghỉ hoặc nghỉ ít hơn dự kiến, gửi yêu cầu để HR xác nhận. Phép và lịch chỉ thay đổi
      sau khi được duyệt. HR cần làm mới dữ liệu nghỉ phép trong bảng công nếu ngày đó đã ghi nhận
      công.
    </p>
    <p v-if="loading" role="status">Đang tải điều chỉnh…</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="stale" class="muted">
      Bấm “Tải lại” ở đầu trang để kiểm tra kết quả trước khi thao tác tiếp.
    </p>
    <ol v-if="entries.length" class="leave-history">
      <li v-for="entry in entries" :key="entry.id">
        <strong>{{ statuses[entry.status] }}</strong> · {{ timeLabel(entry.createdAt) }}
        <p>
          Đã duyệt: {{ dateLabel(entry.originalStart) }} – {{ dateLabel(entry.originalEnd) }} ·
          {{ leavePeriods[entry.originalPeriod] }} ({{ entry.originalUnits / 2 }} ngày).
        </p>
        <p v-if="entry.cancelAll">Đề nghị hủy toàn bộ thời gian nghỉ.</p>
        <p v-else>
          Còn nghỉ: {{ dateLabel(entry.startDate) }} – {{ dateLabel(entry.endDate) }} ·
          {{ leavePeriods[entry.period] }} ({{ entry.totalUnits / 2 }} ngày).
        </p>
        <p v-if="request.leaveType === 'ANNUAL'">
          Phép đề nghị hoàn: {{ (entry.originalUnits - entry.totalUnits) / 2 }} ngày.
        </p>
        <p class="leave-text">Lý do: {{ entry.reason }}</p>
        <p v-if="entry.reviewNote" class="leave-text">Phản hồi: {{ entry.reviewNote }}</p>
      </li>
    </ol>
    <form
      v-if="!loading && request.status === 'APPROVED' && own && !pending"
      @submit.prevent="save('submit')"
    >
      <fieldset class="form-fields" :disabled="saving || stale">
        <div class="field">
          <label for="adjust-kind">Nội dung điều chỉnh</label>
          <select id="adjust-kind" v-model="form.cancelAll">
            <option :value="true">Không nghỉ — hủy toàn bộ</option>
            <option :value="false">Nghỉ ít hơn — giảm thời gian nghỉ</option>
          </select>
        </div>
        <template v-if="!form.cancelAll">
          <p class="muted">
            Chọn khoảng thời gian vẫn nghỉ (một khoảng liên tục, nằm trong đơn đã duyệt).
          </p>
          <div class="field">
            <label for="adjust-period">Thời lượng còn nghỉ</label
            ><select id="adjust-period" v-model="form.period">
              <option v-for="(label, value) in leavePeriods" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="adjust-start">Còn nghỉ từ ngày</label
            ><input
              id="adjust-start"
              v-model="form.startDate"
              type="date"
              :min="request.startDate"
              :max="request.endDate"
              required
            />
          </div>
          <div class="field">
            <label for="adjust-end">Còn nghỉ đến ngày</label
            ><input
              id="adjust-end"
              v-model="form.endDate"
              type="date"
              :min="form.startDate"
              :max="request.endDate"
              :disabled="form.period !== 'FULL_DAY'"
              required
            />
          </div>
        </template>
        <div class="field">
          <label for="adjust-reason">Lý do điều chỉnh</label
          ><textarea
            id="adjust-reason"
            v-model="form.reason"
            rows="3"
            maxlength="2000"
            required
          ></textarea>
        </div>
        <button type="submit" class="button button-primary">
          {{ saving ? 'Đang gửi…' : 'Gửi yêu cầu điều chỉnh' }}
        </button>
      </fieldset>
    </form>
    <template v-if="!loading && pending">
      <p v-if="own" class="muted">
        Yêu cầu đang chờ HR/ADMIN khác xác nhận. Đơn nghỉ hiện tại vẫn có hiệu lực.
      </p>
      <button
        v-if="own"
        type="button"
        class="button button-secondary"
        :disabled="saving || stale"
        @click="save('cancel')"
      >
        Rút yêu cầu điều chỉnh
      </button>
      <form v-else-if="reviewer" @submit.prevent="save('approve')">
        <fieldset class="form-fields" :disabled="saving || stale">
          <div class="field">
            <label for="adjust-note">Phản hồi điều chỉnh (bắt buộc khi từ chối)</label
            ><textarea id="adjust-note" v-model="note" rows="3" maxlength="2000"></textarea>
          </div>
          <div class="button-row">
            <button type="submit" class="button button-primary">Duyệt điều chỉnh</button
            ><button type="button" class="button button-secondary" @click="save('reject')">
              Từ chối điều chỉnh
            </button>
          </div>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
