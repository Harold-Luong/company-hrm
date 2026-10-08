<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { errorMessage } from '@/auth/api.js'
import { safeDestination } from '@/auth/navigation.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const busy = ref(false)
const error = ref('')
async function submit() {
    if (busy.value) return
    error.value = ''
    busy.value = true
    try {
        await auth.login(email.value, password.value)
        password.value = ''
        await router.replace(safeDestination(route.query.redirect))
    } catch (cause) {
        error.value = errorMessage(cause)
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <div class="login-page">
        <section class="login-story" aria-label="Company HRM">
            <div class="brand brand-inverse">
                <span class="brand-icon">C<span>h</span></span><span>company<span class="brand-light">hrm</span></span>
            </div>
            <div class="login-story-content">
                <span class="story-eyebrow">PEOPLE. CONNECTED.</span>
                <h1>Một không gian.<br />Kết nối cả đội ngũ.</h1>
                <p>
                    Nền tảng quản lý nhân sự tập trung, đồng hành cùng doanh nghiệp và những người tạo nên giá
                    trị.
                </p>
                <div class="story-art" aria-hidden="true">
                    <div class="art-orbit"></div>
                    <div class="art-card">
                        <span class="art-icon">
                            <AppIcon name="shield" :size="32" />
                        </span>
                        <div>
                            <strong>Company workspace</strong><span>Kết nối an toàn. Làm việc hiệu quả.</span>
                        </div>
                        <span class="art-check">
                            <AppIcon name="check" :size="16" />
                        </span>
                    </div>
                    <div class="art-caption">
                        <span class="status-dot"></span>Đúng người · Đúng quyền truy cập
                    </div>
                </div>
            </div>
            <div class="story-footer"><span>Company HRM</span><span>Built around people.</span></div>
        </section>
        <main class="login-main">
            <div class="login-mobile-brand brand">
                <span class="brand-icon">C<span>h</span></span><span>Company HRM</span>
            </div>
            <div class="login-form-wrap">
                <span class="login-lock">
                    <AppIcon name="lock" :size="24" />
                </span>
                <p class="eyebrow">CHÀO MỪNG TRỞ LẠI</p>
                <h2>Đăng nhập workspace</h2>
                <p class="muted login-description">
                    Sử dụng tài khoản được cấp để truy cập không gian làm việc của bạn.
                </p>
                <div v-if="route.query.logout === 'local'" class="alert alert-warning" role="status">
                    Đã đăng xuất trên trình duyệt này. Không thể xác nhận thu hồi phiên trên máy chủ do lỗi
                    kết nối.
                </div>
                <div v-else-if="route.query.logout === 'success'" class="alert alert-success" role="status">
                    Bạn đã đăng xuất thành công.
                </div>
                <div v-if="error" id="login-error" class="alert alert-error" role="alert">{{ error }}</div>
                <form :aria-busy="busy" @submit.prevent="submit">
                    <fieldset :disabled="busy" class="form-fields">
                        <label for="email">Email công việc</label>
                        <div class="input-icon">
                            <AppIcon name="mail" :size="18" /><input id="email" v-model="email" type="email"
                                autocomplete="username" placeholder="ten@congty.com" maxlength="255" required
                                :aria-describedby="error ? 'login-error' : undefined" />
                        </div>
                        <label for="password">Mật khẩu</label>
                        <div class="input-icon">
                            <AppIcon name="lock" :size="18" /><input id="password" v-model="password"
                                :type="showPassword ? 'text' : 'password'" autocomplete="current-password"
                                placeholder="Nhập mật khẩu của bạn" required /><button type="button"
                                class="password-toggle" :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                                :aria-pressed="showPassword" @click="showPassword = !showPassword">
                                <AppIcon name="eye" :size="18" />
                            </button>
                        </div>
                        <button type="submit" class="button button-primary login-submit">
                            <span>{{ busy ? 'Đang đăng nhập…' : 'Đăng nhập' }}</span>
                            <AppIcon v-if="!busy" name="arrow" :size="18" />
                        </button>
                    </fieldset>
                </form>
                <p class="login-help">
                    Chưa có tài khoản hoặc quên mật khẩu?<br /><strong>Vui lòng liên hệ HR hoặc quản trị viên.</strong>
                </p>
                <div class="login-security">
                    <AppIcon name="shield" :size="16" /><span>Chỉ dành cho tài khoản nội bộ doanh nghiệp</span>
                </div>
            </div>
            <footer class="login-footer">
                © {{ new Date().getFullYear() }} Company HRM. All rights reserved.
            </footer>
        </main>
    </div>
</template>
