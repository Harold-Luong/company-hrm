<script setup>
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
      (!group.value || feature.group === group.value) &&
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
  <HelpLayout
    title="Chức năng & công thức"
    description="Tra cứu toàn bộ nhóm chức năng đang có trên web, quyền sử dụng và cách tính các chỉ số."
  >
    <div class="help-reference-layout">
      <nav class="panel help-index" aria-label="Mục lục công thức">
        <p class="eyebrow">TRA CỨU NHANH</p>
        <a
          href="#feature-catalog"
          :aria-current="!route.hash || route.hash === '#feature-catalog' ? 'location' : undefined"
          >Danh mục chức năng</a
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
            <h2 id="catalog-title">Danh mục chức năng</h2>
            <p class="muted">
              Có thể đọc tất cả nội dung. Liên kết thao tác chỉ hiển thị khi tài khoản có quyền mở
              trang.
            </p>
            <div class="help-filters">
              <div class="field">
                <label for="help-search">Tìm chức năng hoặc công thức</label>
                <input
                  id="help-search"
                  v-model="search"
                  type="search"
                  placeholder="Ví dụ: tính công, nghỉ phép, OT…"
                />
              </div>
              <div class="field">
                <label for="help-group">Nhóm chức năng</label>
                <select id="help-group" v-model="group">
                  <option value="">Tất cả nhóm</option>
                  <option v-for="item in helpGroups" :key="item" :value="item">{{ item }}</option>
                </select>
              </div>
            </div>
            <p class="small muted help-result-count" role="status">
              {{ filtered.length }} / {{ features.length }} chức năng
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
                <span class="small muted">Cách tính:</span>
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
                :aria-label="`Mở chức năng: ${feature.title}`"
                >Mở chức năng <AppIcon name="arrow" :size="16"
              /></RouterLink>
              <p v-else class="muted small">
                Thao tác cần vai trò
                {{ (feature.roles ?? personnelManagementRoles).join(' hoặc ') }}.
              </p>
            </article>
            <div v-if="!filtered.length" class="panel help-card help-empty">
              <h3>Không tìm thấy chức năng phù hợp</h3>
              <p>Thử từ khóa khác hoặc bỏ bộ lọc nhóm.</p>
              <button class="button button-secondary" @click="reset">Xóa bộ lọc</button>
            </div>
          </div>
        </section>
        <section class="help-formulas" aria-labelledby="formulas-title">
          <div class="help-section-heading">
            <p class="eyebrow">CÁCH HỆ THỐNG TÍNH SỐ LIỆU</p>
            <h2 id="formulas-title">Công thức & ví dụ</h2>
            <p class="muted">
              Các quy tắc đang áp dụng trong ứng dụng. Giờ làm việc theo múi giờ Việt Nam; bảng công
              hiện là tạm tính.
            </p>
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
              <strong>Ví dụ</strong>
              <p>{{ formula.example }}</p>
            </div>
            <ul class="help-rules">
              <li v-for="note in formula.notes" :key="note">{{ note }}</li>
            </ul>
            <a class="text-button" href="#feature-catalog">↑ Về danh mục chức năng</a>
          </article>
        </section>
      </div>
    </div>
  </HelpLayout>
</template>
