import { createI18n } from 'vue-i18n'
import { watch } from 'vue'
import vi from '../locales/vi.json'
import en from '../locales/en.json'

export const supportedLocales = ['vi', 'en']
export const defaultLocale = 'vi'
export const localeStorageKey = 'locale'
const isSupported = (value) => supportedLocales.includes(value)
function savedLocale() {
    try {
        const value = localStorage.getItem(localeStorageKey)
        return isSupported(value) ? value : defaultLocale
    } catch {
        return defaultLocale
    }
}

const i18n = createI18n({
    legacy: false,
    locale: savedLocale(),
    fallbackLocale: defaultLocale,
    messages: { vi, en },
    missingWarn: false,
    fallbackWarn: false,
})

export const translate = (key, parameters = {}) => i18n.global.t(key, parameters)
export const getDisplayLocale = () => (i18n.global.locale.value === 'en' ? 'en-US' : 'vi-VN')

function persistLocale(locale) {
    document.documentElement.lang = locale
    try {
        localStorage.setItem(localeStorageKey, locale)
    } catch {
        // Language changes still work when browser storage is unavailable.
    }
}
export function setLocale(locale) {
    if (!isSupported(locale)) return false
    i18n.global.locale.value = locale
    persistLocale(locale)
    return true
}
export function toggleLocale() {
    setLocale(i18n.global.locale.value === 'vi' ? 'en' : 'vi')
}

export function startLocaleSync() {
    const stop = watch(i18n.global.locale, persistLocale, { immediate: true, flush: 'sync' })
    const onStorage = (event) => {
        if (event.key === localeStorageKey && isSupported(event.newValue)) {
            i18n.global.locale.value = event.newValue
        }
    }
    window.addEventListener('storage', onStorage)
    return () => {
        stop()
        window.removeEventListener('storage', onStorage)
    }
}

export default i18n
