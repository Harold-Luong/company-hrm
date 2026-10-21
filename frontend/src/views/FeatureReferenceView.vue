<script setup>
import { featureReference as copy } from '@/content/workspace.js'

import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/auth/session.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import HelpLayout from '@/components/HelpLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { features, formulas, helpGroups } from '@/help/content.js'

const search = ref('')
const route = useRoute()
const group = ref('')
const normalize = (value) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLocaleLowerCase('vi')
const formulaById = Object.fromEntries(formulas.map((formula) => [formula.id, formula]))
const filtered = computed(() => {
  const query = normalize(search.value.trim())
  return features.filter((feature) => {
    const related = (feature.formulaIds ?? []).map((id) => formulaById[id])
    return (
      (!group.value || feature.groupId === group.value) &&
      normalize(
        [
          feature.title,
          feature.description,
          feature.access,
          ...feature.rules,
          ...related.flatMap((formula) => [
            formula.title,
            formula.expression,
            formula.explanation,
            formula.example,
            ...formula.notes,
          ]),
        ].join(' '),
      ).includes(query)
    )
  })
})
function reset() {
  search.value = ''
  group.value = ''
}
</script>

<template>
  <HelpLayout :title="copy.title" :description="copy.descriptionText">
    <div class="help-reference-layout">
      <nav class="panel help-index" :aria-label="copy.contentsLabel">
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <a
          href="#feature-catalog"
          :aria-current="!route.hash || route.hash === '#feature-catalog' ? 'location' : undefined"
          >{{ copy.featuresTitle }}</a
        >
        <a
          v-for="formula in formulas"
          :key="formula.id"
          :href="`#${formula.id}`"
          :aria-current="route.hash === `#${formula.id}` ? 'location' : undefined"
          >{{ formula.title }}</a
        >
      </nav>
      <div class="help-reference-content">
        <section id="feature-catalog" aria-labelledby="catalog-title">
          <div class="panel help-card">
            <h2 id="catalog-title">{{ copy.featuresTitle }}</h2>
            <p class="muted">{{ copy.description }}</p>
            <div class="help-filters">
              <div class="field">
                <label for="help-search">{{ copy.searchLabel }}</label>
                <input
                  id="help-search"
                  v-model="search"
                  type="search"
                  :placeholder="copy.searchPlaceholder"
                />
              </div>
              <div class="field">
                <label for="help-group">{{ copy.groupLabel }}</label>
                <select id="help-group" v-model="group">
                  <option value="">{{ copy.allGroups }}</option>
                  <option v-for="item in helpGroups" :key="item.id" :value="item.id">
                    {{ item.label }}
                  </option>
                </select>
              </div>
            </div>
            <p class="small muted help-result-count" role="status">
              {{ copy.resultCount({ visible: filtered.length, total: features.length }) }}
            </p>
          </div>
          <div class="help-feature-list">
            <article v-for="feature in filtered" :key="feature.id" class="panel help-card">
              <p class="help-audience">{{ feature.group }} · {{ feature.access }}</p>
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.description }}</p>
              <ul class="help-rules">
                <li v-for="rule in feature.rules" :key="rule">{{ rule }}</li>
              </ul>
              <div v-if="feature.formulaIds?.length" class="help-formula-links">
                <span class="small muted">{{ copy.formulaLabel }}</span>
                <a v-for="id in feature.formulaIds" :key="id" :href="`#${id}`">{{
                  formulaById[id].title
                }}</a>
              </div>
              <RouterLink
                v-if="
                  !feature.restricted || auth.hasRole(feature.roles ?? personnelManagementRoles)
                "
                :to="feature.path"
                class="text-button help-open"
                :aria-label="copy.openFeatureLabel({ title: feature.title })"
                >{{ copy.openFeature }} <AppIcon name="arrow" :size="16"
              /></RouterLink>
              <p v-else class="muted small">
                {{
                  copy.roleRequired({
                    roles: (feature.roles ?? personnelManagementRoles).join(copy.roleSeparator),
                  })
                }}
              </p>
            </article>
            <div v-if="!filtered.length" class="panel help-card help-empty">
              <h3>{{ copy.empty }}</h3>
              <p>{{ copy.emptyHint }}</p>
              <button class="button button-secondary" @click="reset">
                {{ copy.clearFilters }}
              </button>
            </div>
          </div>
        </section>
        <section class="help-formulas" aria-labelledby="formulas-title">
          <div class="help-section-heading">
            <p class="eyebrow">{{ copy.formulasEyebrow }}</p>
            <h2 id="formulas-title">{{ copy.formulasTitle }}</h2>
            <p class="muted">{{ copy.formulasDescription }}</p>
          </div>
          <article
            v-for="formula in formulas"
            :id="formula.id"
            :key="formula.id"
            class="panel help-card"
          >
            <h3>{{ formula.title }}</h3>
            <p>{{ formula.explanation }}</p>
            <pre class="help-expression">{{ formula.expression }}</pre>
            <div class="help-example">
              <strong>{{ copy.example }}</strong>
              <p>{{ formula.example }}</p>
            </div>
            <ul class="help-rules">
              <li v-for="note in formula.notes" :key="note">{{ note }}</li>
            </ul>
            <a class="text-button" href="#feature-catalog">{{ copy.backToFeatures }}</a>
          </article>
        </section>
      </div>
    </div>
  </HelpLayout>
</template>
