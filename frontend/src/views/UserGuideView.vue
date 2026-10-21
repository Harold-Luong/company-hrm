<script setup>
import { userGuide as copy } from '@/content/workspace.js'

import { useRoute } from 'vue-router'
import { auth } from '@/auth/session.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import HelpLayout from '@/components/HelpLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { guides, helpQuestions } from '@/help/content.js'
const route = useRoute()
</script>

<template>
    <HelpLayout :title="copy.title" :description="copy.descriptionText">
        <section class="help-intro panel" aria-labelledby="help-start">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h2 id="help-start">{{ copy.tasksTitle }}</h2>
                <p>{{ copy.description }}</p>
            </div>
            <RouterLink to="/help/features" class="button button-primary">
                {{ copy.referenceLink }}
                <AppIcon name="arrow" :size="17" />
            </RouterLink>
        </section>
        <nav class="help-topics" :aria-label="copy.contentsLabel">
            <a
v-for="guide in guides" :key="guide.id" :href="`#${guide.id}`"
                :aria-current="route.hash === `#${guide.id}` ? 'location' : undefined">{{ guide.title }}</a>
            <a href="#common-questions" :aria-current="route.hash === '#common-questions' ? 'location' : undefined">{{
                copy.faq }}</a>
        </nav>
        <div class="help-guide-grid">
            <section
v-for="guide in guides" :id="guide.id" :key="guide.id" class="panel help-card"
                :aria-labelledby="`${guide.id}-title`">
                <div class="help-card-heading">
                    <span class="help-icon">
                        <AppIcon :name="guide.icon" />
                    </span>
                    <div>
                        <p class="help-audience">
                            {{ guide.restricted ? copy.managementRoles : copy.allEmployees }}
                        </p>
                        <h2 :id="`${guide.id}-title`">{{ guide.title }}</h2>
                    </div>
                </div>
                <ol class="help-steps">
                    <li v-for="step in guide.steps" :key="step">{{ step }}</li>
                </ol>
                <p class="help-note">{{ guide.note }}</p>
                <RouterLink
v-if="!guide.restricted || auth.hasRole(personnelManagementRoles)" :to="guide.path"
                    class="text-button help-open" :aria-label="copy.openFeatureLabel({ title: guide.title })">
                    {{ copy.openFeature }}
                    <AppIcon name="arrow" :size="16" />
                </RouterLink>
                <p v-else class="muted small">{{ copy.managementOnly }}</p>
            </section>
        </div>
        <section id="common-questions" class="panel help-card help-faq" aria-labelledby="faq-title">
            <p class="eyebrow">{{ copy.faqEyebrow }}</p>
            <h2 id="faq-title">{{ copy.faq }}</h2>
            <details v-for="[question, answer] in helpQuestions" :key="question">
                <summary>{{ question }}</summary>
                <p>{{ answer }}</p>
            </details>
            <RouterLink to="/services" class="text-button help-open">{{ copy.servicesLink }}</RouterLink>
        </section>
    </HelpLayout>
</template>
