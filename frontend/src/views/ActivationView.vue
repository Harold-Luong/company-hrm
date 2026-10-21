<script setup>
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'
import { activationPassword } from '@/config/validation.js'

import { activation as copy } from '@/content/auth.js'
import { common } from '@/content/common.js'

import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { activateAccount } from '@/auth/api.js'
import { errorMessage } from '@/api/errors.js'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const router = useRouter()
// Keep the invitation credential only in this page's memory, never in browser storage.
let token = typeof route.query.token === 'string' ? route.query.token : ''
const validToken = /^[A-Za-z0-9_-]{43}$/.test(token)
void router.replace({ name: 'activate', query: {}, hash: '' })
const password = ref('')
const confirmation = ref('')
const showPassword = ref(false)
const busy = ref(false)
const activated = ref(false)
const error = useLocalizedMessage(() => (validToken ? '' : copy.invalidToken))
const controller = new AbortController()

onBeforeUnmount(() => {
    controller.abort()
    token = ''
    password.value = ''
    confirmation.value = ''
})

async function submit() {
    if (busy.value || !validToken || activated.value) return
    error.value = ''
    if (
        !password.value.trim() ||
        password.value.length < activationPassword.minLength ||
        new TextEncoder().encode(password.value).length > activationPassword.maxBytes
    ) {
        error.value = () => copy.invalidPassword
        return
    }
    if (password.value !== confirmation.value) {
        error.value = () => copy.passwordMismatch
        return
    }
    busy.value = true
    try {
        await activateAccount(token, password.value, controller.signal)
        token = ''
        password.value = ''
        confirmation.value = ''
        activated.value = true
    } catch (cause) {
        if (controller.signal.aborted) return
        if (cause.message === 'Invalid or expired activation token') {
            error.value = () => copy.expiredToken
        } else if (cause.status === 0) {
            error.value = () => copy.uncertainResult
        } else if (cause.status === 429) {
            error.value = () => errorMessage(cause)
        } else {
            error.value = () => (cause.status >= 500 ? copy.unavailable : copy.failed)
        }
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <div class="login-page">
        <section class="login-story" :aria-label="common.appName">
            <div class="brand brand-inverse">
                <span class="brand-icon">{{ common.monogramFirst }}<span>{{ common.monogramSecond }}</span></span>
                <span>{{ common.wordmarkPrefix
                }}<span class="brand-light">{{ common.wordmarkSuffix }}</span></span>
            </div>
            <div class="login-story-content">
                <span class="story-eyebrow">{{ common.tagline }}</span>
                <h1>{{ copy.welcomeFirstLine }}<br />{{ copy.welcomeSecondLine }}</h1>
                <p>{{ copy.welcomeDescription }}</p>
            </div>
            <div class="story-footer">
                <span>{{ common.appName }}</span><span>{{ common.brandFooter }}</span>
            </div>
        </section>
        <main class="login-main">
            <LanguageSwitcher class="auth-language" />
            <div class="login-mobile-brand brand">
                <span class="brand-icon">{{ common.monogramFirst }}<span>{{ common.monogramSecond
                }}</span></span><span>{{ common.appName }}</span>
            </div>
            <div class="login-form-wrap">
                <span class="login-lock">
                    <AppIcon :name="activated ? 'check' : 'lock'" :size="24" />
                </span>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h2>{{ activated ? copy.successTitle : copy.title }}</h2>
                <template v-if="activated">
                    <p class="alert alert-success" role="status">{{ copy.successNotice }}</p>
                    <RouterLink :to="{ name: 'login' }" class="button button-primary login-submit">
                        {{ copy.login }}
                        <AppIcon name="arrow" :size="18" />
                    </RouterLink>
                </template>
                <template v-else>
                    <p class="muted login-description">{{ copy.description }}</p>
                    <div v-if="error" id="activation-error" class="alert alert-error" role="alert">
                        {{ error }}
                    </div>
                    <form v-if="validToken" :aria-busy="busy" @submit.prevent="submit">
                        <fieldset :disabled="busy" class="form-fields">
                            <label for="activation-password">{{ copy.newPassword }}</label>
                            <div class="input-icon">
                                <AppIcon name="lock" :size="18" />
                                <input
id="activation-password" v-model="password"
                                    :type="showPassword ? 'text' : 'password'" autocomplete="new-password"
                                    :minlength="activationPassword.minLength" :maxlength="activationPassword.maxBytes"
                                    required
                                    :aria-describedby="error ? 'password-help activation-error' : 'password-help'" />
                                <button
type="button" class="password-toggle"
                                    :aria-label="showPassword ? common.hidePassword : common.showPassword"
                                    :aria-pressed="showPassword" @click="showPassword = !showPassword">
                                    <AppIcon name="eye" :size="18" />
                                </button>
                            </div>
                            <p id="password-help" class="muted">{{ copy.passwordHint }}</p>
                            <label for="activation-confirmation">{{ copy.confirmation }}</label>
                            <div class="input-icon">
                                <AppIcon name="lock" :size="18" />
                                <input
id="activation-confirmation" v-model="confirmation"
                                    :type="showPassword ? 'text' : 'password'" autocomplete="new-password"
                                    :maxlength="activationPassword.maxBytes" required
                                    :aria-describedby="error ? 'activation-error' : undefined" />
                            </div>
                            <button type="submit" class="button button-primary login-submit">
                                {{ busy ? copy.actions.submitting : copy.actions.submit }}
                            </button>
                        </fieldset>
                    </form>
                    <p class="login-help">
                        {{ copy.alreadyActivated }}
                        <RouterLink :to="{ name: 'login' }">{{ copy.login }}</RouterLink>
                    </p>
                </template>
            </div>
            <footer class="login-footer">{{ copy.copyright({ year: new Date().getFullYear() }) }}</footer>
        </main>
    </div>
</template>
