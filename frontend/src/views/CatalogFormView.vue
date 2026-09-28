<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hrm } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
const props = defineProps({
  resource: { type: String, required: true },
  id: { type: String, default: '' },
})
const router = useRouter()
const title = computed(() => (props.resource === 'departments' ? 'phòng ban' : 'chức danh'))
const form = reactive({ code: '', name: '', description: '' })
const loading = ref(!!props.id)
const loaded = ref(!props.id)
const busy = ref(false)
const error = ref('')
const success = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    Object.assign(form, await hrm.get(props.resource, props.id))
    loaded.value = true
  } catch (cause) {
    error.value = serviceError(cause)
  } finally {
    loading.value = false
  }
}
async function save() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await hrm.save(props.resource, props.id, {
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description?.trim() || null,
    })
    if (!props.id) await router.replace(`/${props.resource}/${result.id}`)
    else {
      Object.assign(form, result)
      success.value = 'Đã lưu thay đổi.'
    }
  } catch (cause) {
    error.value = serviceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  if (props.id) load()
})
</script>
<template>
  <div>
    <RouterLink class="back-link" :to="`/${resource}`">← Danh sách {{ title }}</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">DANH MỤC TỔ CHỨC</p>
        <h1>{{ id ? 'Chi tiết' : 'Thêm' }} {{ title }}</h1>
        <p class="muted">Mã và tên là thông tin bắt buộc.</p>
      </div>
    </div>
    <form class="panel create-form narrow-form" :aria-busy="loading || busy" @submit.prevent="save">
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
      <p v-if="loading" role="status">Đang tải thông tin…</p>
      <button v-else-if="!loaded" type="button" class="button button-secondary" @click="load">
        Thử lại
      </button>
      <fieldset v-if="loaded" class="form-fields" :disabled="busy">
        <label for="catalog-code">Mã {{ title }} *</label
        ><input id="catalog-code" v-model="form.code" required maxlength="50" pattern=".*\S.*" />
        <label for="catalog-name">Tên {{ title }} *</label
        ><input id="catalog-name" v-model="form.name" required maxlength="150" pattern=".*\S.*" />
        <label for="catalog-description">Mô tả</label
        ><textarea id="catalog-description" v-model="form.description" rows="4"></textarea>
        <div class="form-footer">
          <RouterLink class="button button-secondary" :to="`/${resource}`">Quay lại</RouterLink
          ><button class="button button-primary" type="submit">
            {{ busy ? 'Đang lưu…' : id ? 'Lưu thay đổi' : 'Tạo mới' }}
          </button>
        </div>
      </fieldset>
    </form>
  </div>
</template>
