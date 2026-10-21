<script setup>
import { accountResultToasts as copy } from '@/content/workspace.js'

import {
    accountToasts,
    dismissAccountToast,
    provisioningFailures,
} from '@/notifications/accountResults.js'
</script>
<template>
    <div class="account-toasts" aria-live="polite" aria-relevant="additions">
        <div v-for="item in accountToasts" :key="item.requestId" class="account-toast" role="status">
            <div>
                <strong>{{
                    item.provisioningStatus === 'SUCCEEDED' ? copy.successTitle : copy.failureTitle
                    }}</strong>
                <p>
                    {{
                        item.provisioningStatus === 'SUCCEEDED'
                            ? copy.activationHint
                            : provisioningFailures[item.errorCode] || copy.failureHint
                    }}
                </p>
                <RouterLink :to="`/employees/${item.employeeId}`">{{ copy.viewEmployee }}</RouterLink>
            </div>
            <button
type="button" class="icon-button" :aria-label="copy.dismiss"
                @click="dismissAccountToast(item.requestId)">
                ×
            </button>
        </div>
    </div>
</template>
<style scoped>
.account-toasts {
    position: fixed;
    right: 1rem;
    bottom: 1rem;
    z-index: 100;
    width: min(25rem, calc(100vw - 2rem));
    max-height: 70vh;
    overflow-y: auto;
    display: grid;
    gap: 0.75rem;
}

.account-toast {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    background: var(--surface, #fff);
    color: var(--text, #172b4d);
    border: 1px solid #cbd5e1;
    border-radius: 0.75rem;
    padding: 1rem;
    box-shadow: 0 4px 20px #0002;
}

.account-toast p {
    margin: 0.5rem 0;
    font-size: 0.875rem;
}

.account-toast button {
    flex-shrink: 0;
}
</style>
