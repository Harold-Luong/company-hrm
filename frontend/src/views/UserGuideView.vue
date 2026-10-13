<script setup>
import { useRoute } from 'vue-router'
import { auth } from '@/auth/session.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import HelpLayout from '@/components/HelpLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { guides, helpQuestions } from '@/help/content.js'
const route = useRoute()
</script>

<template>
  <HelpLayout
    title="Hướng dẫn sử dụng"
    description="Từng bước thực hiện công việc hằng ngày và xử lý các tình huống thường gặp."
  >
    <section class="help-intro panel" aria-labelledby="help-start">
      <div>
        <p class="eyebrow">BẮT ĐẦU TỪ ĐÂY</p>
        <h2 id="help-start">Chọn việc bạn muốn thực hiện</h2>
        <p>
          Hướng dẫn dùng chung cho nhân viên và bộ phận quản lý. Các tác vụ HR / ADMIN được ghi rõ
          bên dưới.
        </p>
      </div>
      <RouterLink to="/help/features" class="button button-primary">
        Tra chức năng & công thức <AppIcon name="arrow" :size="17" />
      </RouterLink>
    </section>
    <nav class="help-topics" aria-label="Mục lục hướng dẫn">
      <a
        v-for="guide in guides"
        :key="guide.id"
        :href="`#${guide.id}`"
        :aria-current="route.hash === `#${guide.id}` ? 'location' : undefined"
        >{{ guide.title }}</a
      >
      <a
        href="#common-questions"
        :aria-current="route.hash === '#common-questions' ? 'location' : undefined"
        >Câu hỏi thường gặp</a
      >
    </nav>
    <div class="help-guide-grid">
      <section
        v-for="guide in guides"
        :id="guide.id"
        :key="guide.id"
        class="panel help-card"
        :aria-labelledby="`${guide.id}-title`"
      >
        <div class="help-card-heading">
          <span class="help-icon"><AppIcon :name="guide.icon" /></span>
          <div>
            <p class="help-audience">
              {{ guide.restricted ? 'HR / ADMIN' : 'Dành cho mọi nhân viên' }}
            </p>
            <h2 :id="`${guide.id}-title`">{{ guide.title }}</h2>
          </div>
        </div>
        <ol class="help-steps">
          <li v-for="step in guide.steps" :key="step">{{ step }}</li>
        </ol>
        <p class="help-note">{{ guide.note }}</p>
        <RouterLink
          v-if="!guide.restricted || auth.hasRole(personnelManagementRoles)"
          :to="guide.path"
          class="text-button help-open"
          :aria-label="`Mở chức năng: ${guide.title}`"
        >
          Mở chức năng <AppIcon name="arrow" :size="16" />
        </RouterLink>
        <p v-else class="muted small">Thao tác cần vai trò HR hoặc ADMIN.</p>
      </section>
    </div>
    <section id="common-questions" class="panel help-card help-faq" aria-labelledby="faq-title">
      <p class="eyebrow">GIẢI ĐÁP NHANH</p>
      <h2 id="faq-title">Câu hỏi thường gặp</h2>
      <details v-for="[question, answer] in helpQuestions" :key="question">
        <summary>{{ question }}</summary>
        <p>{{ answer }}</p>
      </details>
      <RouterLink to="/services" class="text-button help-open"
        >Kiểm tra kết nối dịch vụ →</RouterLink
      >
    </section>
  </HelpLayout>
</template>
