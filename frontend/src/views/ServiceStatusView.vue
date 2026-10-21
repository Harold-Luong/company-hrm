<script setup>
import { getDisplayLocale } from '@/i18n/index.js'

import { serviceNames, serviceStatus as copy } from '@/content/services.js'
import { common } from '@/content/common.js'

import { onMounted, ref } from 'vue'
import { serviceError } from '@/api/errors.js'
import { getServiceHealth, serviceDefinitions } from '@/services/api.js'
const busy = ref(false)
const checkedAt = ref('')
const services = ref(serviceDefinitions.map((service) => ({ ...service, status: '', error: '' })))
async function check() {
    if (busy.value) return
    busy.value = true
    await Promise.all(
        services.value.map(async (service) => {
            service.error = ''
            service.status = ''
            try {
                const data = await getServiceHealth(service.id)
                service.status =
                    data.status === 'UP' && (!service.requiresDatabaseStatus || data.database === 'UP')
                        ? 'healthy'
                        : 'unavailable'
            } catch (cause) {
                service.error = cause
            }
        }),
    )
    checkedAt.value = new Date()
    busy.value = false
}
onMounted(check)
</script>
<template>
    <div>
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ copy.title }}</h1>
                <p class="muted">{{ copy.description }}</p>
            </div>
            <button class="button button-secondary" :disabled="busy" @click="check">
                {{ busy ? common.checking : copy.retry }}
            </button>
        </div>
        <div class="quick-grid" :aria-busy="busy">
            <section v-for="service in services" :key="service.id" class="panel create-form">
                <h2>{{ serviceNames[service.id] }}</h2>
                <p v-if="service.error" class="alert alert-error" role="alert">
                    {{ serviceError(service.error) }}
                </p>
                <p v-else role="status">{{ service.status ? copy[service.status] : common.checking }}</p>
            </section>
        </div>
        <p v-if="checkedAt" class="muted small section-block">
            {{ copy.checkedAt({ checkedAt: checkedAt.toLocaleString(getDisplayLocale()) }) }}
        </p>
        <div class="info-strip">
            <p>{{ copy.coverageHint }}</p>
        </div>
    </div>
</template>
