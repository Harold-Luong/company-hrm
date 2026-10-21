<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { catalogForm as copy } from '@/content/hrm.js'
import { common } from '@/content/common.js'

import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hrm } from '@/hrm/api.js'
import { serviceError } from '@/api/errors.js'
const props = defineProps({
  resource: { type: String, required: true },
  id: { type: String, default: '' },
})
const router = useRouter()
const title = computed(() => (props.resource === 'departments' ? copy.department : copy.position))
const form = reactive({ code: '', name: '', description: '' })
const loading = ref(!!props.id)
const loaded = ref(!props.id)
const busy = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
async function load() {
  loading.value = true
  error.value = ''
  try {
    Object.assign(form, await hrm.get(props.resource, props.id))
    loaded.value = true
  } catch (cause) {
    error.value = () => serviceError(cause)
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
      success.value = () => copy.saved
    }
  } catch (cause) {
    error.value = () => serviceError(cause)
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
    <RouterLink class="back-link" :to="`/${resource}`">{{
      copy.back({ title: title })
    }}</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ id ? copy.detailTitle : copy.createTitle }} {{ title }}</h1>
        <p class="muted">{{ copy.requiredHint }}</p>
      </div>
    </div>
    <form class="panel create-form narrow-form" :aria-busy="loading || busy" @submit.prevent="save">
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
      <p v-if="loading" role="status">{{ copy.loading }}</p>
      <button v-else-if="!loaded" type="button" class="button button-secondary" @click="load">
        {{ common.retry }}
      </button>
      <fieldset v-if="loaded" class="form-fields" :disabled="busy">
        <label for="catalog-code">{{ copy.code({ title: title }) }}</label
        ><input
          id="catalog-code"
          v-model="form.code"
          required
          :maxlength="fieldLimits.catalogCode"
          pattern=".*\S.*"
        />
        <label for="catalog-name">{{ copy.name({ title: title }) }}</label
        ><input
          id="catalog-name"
          v-model="form.name"
          required
          :maxlength="fieldLimits.catalogName"
          pattern=".*\S.*"
        />
        <label for="catalog-description">{{ copy.description }}</label
        ><textarea id="catalog-description" v-model="form.description" rows="4"></textarea>
        <div class="form-footer">
          <RouterLink class="button button-secondary" :to="`/${resource}`">{{
            common.back
          }}</RouterLink
          ><button class="button button-primary" type="submit">
            {{ busy ? common.saving : id ? common.saveChanges : copy.create }}
          </button>
        </div>
      </fieldset>
    </form>
  </div>
</template>
