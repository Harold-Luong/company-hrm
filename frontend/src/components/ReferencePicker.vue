<script setup>
import { onMounted, ref } from 'vue'
import { hrm, fullName } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
const props = defineProps({
  modelValue: { type: String, default: '' },
  resource: { type: String, required: true },
  label: { type: String, required: true },
  exclude: { type: String, default: '' },
  selectedLabel: { type: String, default: '' },
})
defineEmits(['update:modelValue'])
const rows = ref([])
const page = ref(-1)
const pages = ref(1)
const busy = ref(false)
const error = ref('')
async function more() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const data = await hrm.list(props.resource, page.value + 1, 100)
    rows.value.push(...data.content)
    page.value = data.page
    pages.value = data.totalPages
  } catch (cause) {
    error.value = serviceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(more)
</script>
<template>
  <div class="field">
    <label :for="`reference-${resource}`">{{ label }}</label>
    <select
      :id="`reference-${resource}`"
      :value="modelValue"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option value="">Không chọn</option>
      <option v-if="modelValue && !rows.some((row) => row.id === modelValue)" :value="modelValue">
        {{ selectedLabel || modelValue }}
      </option>
      <option v-for="row in rows.filter((row) => row.id !== exclude)" :key="row.id" :value="row.id">
        {{
          resource === 'employees'
            ? `${row.employeeCode} · ${fullName(row)}`
            : `${row.code} · ${row.name}`
        }}
      </option>
    </select>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <button
      v-if="error || page + 1 < pages"
      type="button"
      class="text-button"
      :disabled="busy"
      @click="more"
    >
      {{ busy ? 'Đang tải…' : error ? 'Thử tải lại lựa chọn' : 'Tải thêm lựa chọn' }}
    </button>
  </div>
</template>
