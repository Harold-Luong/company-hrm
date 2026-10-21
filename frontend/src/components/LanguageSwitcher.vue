<script setup>
import { useI18n } from 'vue-i18n'
import { toggleLocale } from '@/i18n/index.js'
import AppIcon from './AppIcon.vue'

defineProps({ menu: Boolean })
const { t, locale } = useI18n({ useScope: 'global' })
</script>

<template>
    <button
type="button" :class="menu ? 'account-dropdown-link' : 'language-switcher'" :aria-label="locale === 'vi'
        ? t('workspaceLayout.switchToEnglish')
        : t('workspaceLayout.switchToVietnamese')
        " @click="toggleLocale">
        <AppIcon name="language" :size="18" />
        {{ locale === 'vi' ? t('workspaceLayout.english') : t('workspaceLayout.vietnamese') }}
    </button>
</template>

<style scoped>
.language-switcher {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border: 1px solid currentColor;
    border-radius: 0.5rem;
    background: transparent;
    color: inherit;
    cursor: pointer;
}
</style>
