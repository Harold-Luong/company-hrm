<script setup>
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'
import { fieldLimits } from '@/config/validation.js'

import { login as copy } from '@/content/auth.js'
import { common } from '@/content/common.js'

import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { errorMessage } from '@/api/errors.js'
import { safeDestination } from '@/auth/navigation.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const busy = ref(false)
const error = useLocalizedMessage()
async function submit() {
    if (busy.value) return
    error.value = ''
    busy.value = true
    try {
        await auth.login(email.value, password.value)
        password.value = ''
        await router.replace(safeDestination(route.query.redirect))
    } catch (cause) {
        error.value = () => errorMessage(cause)
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <div class="login-page">
        <section class="login-story" :aria-label="common.appName">
            <div class="brand brand-inverse">
                <span class="brand-icon">{{ common.monogramFirst }}<span>{{ common.monogramSecond
                        }}</span></span><span>{{ common.wordmarkPrefix
                        }}<span class="brand-light">{{ common.wordmarkSuffix }}</span></span>
            </div>
            <div class="login-story-content">
                <span class="story-eyebrow">{{ common.tagline }}</span>
                <h1>{{ copy.welcomeFirstLine }}<br />{{ copy.welcomeSecondLine }}</h1>
                <p>{{ copy.welcomeDescription }}</p>
                <div class="story-art" aria-hidden="true">
                    <div class="art-orbit"></div>
                    <div class="art-card">
                        <span class="art-icon">
                            <AppIcon name="shield" :size="32" />
                        </span>

                        <div>
                            <strong>{{ common.workspaceName }}</strong><span>{{ copy.securityTagline }}</span>
                        </div>
                        <span class="art-check">
                            <AppIcon name="check" :size="16" />
                        </span>
                    </div>
                    <div class="art-caption"><span class="status-dot"></span>{{ copy.accessTagline }}</div>
                </div>
            </div>
            <div class="story-footer">
                <span>{{ common.appName }}</span><span>{{ common.brandFooter }}</span>
            </div>
        </section>
        <main class="login-main">
            <div class="auth-language">
                <LanguageSwitcher />
            </div>

            <div class="login-mobile-brand brand">
                <span class="brand-icon">{{ common.monogramFirst }}<span>{{ common.monogramSecond
                        }}</span></span><span>{{ common.appName }}</span>
            </div>
            <div class="login-form-wrap">
                <span class="login-lock">
                    <AppIcon name="lock" :size="24" />

                </span>

                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h2>{{ copy.title }}</h2>
                <p class="muted login-description">{{ copy.description }}</p>
                <div v-if="route.query.logout === 'local'" class="alert alert-warning" role="status">
                    {{ copy.localLogout }}
                </div>
                <div v-else-if="route.query.logout === 'success'" class="alert alert-success" role="status">
                    {{ copy.signedOut }}
                </div>
                <div v-else-if="auth.state.sessionEnded" class="alert alert-warning" role="status">
                    {{ copy.sessionEnded }}
                </div>
                <div v-if="error" id="login-error" class="alert alert-error" role="alert">{{ error }}</div>
                <form :aria-busy="busy" @submit.prevent="submit">
                    <fieldset :disabled="busy" class="form-fields">
                        <label for="email">{{ common.workEmail }}</label>
                        <div class="input-icon">
                            <AppIcon name="mail" :size="18" /><input
id="email" v-model="email" type="email"
                                autocomplete="username" :placeholder="common.emailPlaceholder"
                                :maxlength="fieldLimits.email" required
                                :aria-describedby="error ? 'login-error' : undefined" />
                        </div>
                        <label for="password">{{ common.password }}</label>
                        <div class="input-icon">
                            <AppIcon name="lock" :size="18" /><input
id="password" v-model="password"
                                :type="showPassword ? 'text' : 'password'" autocomplete="current-password"
                                :placeholder="copy.passwordPlaceholder" required /><button
type="button"
                                class="password-toggle"
                                :aria-label="showPassword ? common.hidePassword : common.showPassword"
                                :aria-pressed="showPassword" @click="showPassword = !showPassword">
                                <AppIcon name="eye" :size="18" />
                            </button>
                        </div>
                        <button type="submit" class="button button-primary login-submit">
                            <span>{{ busy ? copy.submitting : copy.submit }}</span>
                            <AppIcon v-if="!busy" name="arrow" :size="18" />
                        </button>
                    </fieldset>
                </form>
                <p class="login-help">
                    {{ copy.accountHelp }}<br /><strong>{{ copy.contactHelp }}</strong>
                </p>
                <div class="login-security">
                    <AppIcon name="shield" :size="16" /><span>{{ copy.internalOnly }}</span>
                </div>
            </div>
            <footer class="login-footer">{{ copy.copyright({ year: new Date().getFullYear() }) }}</footer>
        </main>
    </div>
</template>
<style>
.auth-language {
    position: absolute;
    top: 1rem;
    right: 1rem;
}
</style>
