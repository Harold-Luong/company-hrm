<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authRequest, errorMessage } from '@/auth/api.js'
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
const error = ref(
  validToken ? '' : 'Liên kết kích hoạt không hợp lệ. Vui lòng mở đầy đủ liên kết trong email.',
)
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
    password.value.length < 12 ||
    new TextEncoder().encode(password.value).length > 72
  ) {
    error.value = 'Mật khẩu cần ít nhất 12 ký tự và không vượt quá 72 byte UTF-8.'
    return
  }
  if (password.value !== confirmation.value) {
    error.value = 'Mật khẩu nhập lại chưa khớp.'
    return
  }
  busy.value = true
  try {
    await authRequest('/activate', {
      method: 'POST',
      credentials: 'omit',
      signal: controller.signal,
      body: JSON.stringify({ token, password: password.value }),
    })
    token = ''
    password.value = ''
    confirmation.value = ''
    activated.value = true
  } catch (cause) {
    if (controller.signal.aborted) return
    if (cause.message === 'Invalid or expired activation token') {
      error.value =
        'Liên kết đã hết hạn, đã sử dụng hoặc bị thu hồi. Nếu bạn đã kích hoạt trước đó, hãy thử đăng nhập; nếu chưa, liên hệ HR để gửi lại lời mời.'
    } else if (cause.status === 0) {
      error.value =
        'Chưa nhận được kết quả. Bạn có thể thử đăng nhập bằng mật khẩu vừa đặt; nếu chưa được, hãy gửi lại biểu mẫu.'
    } else if (cause.status === 429) {
      error.value = errorMessage(cause)
    } else {
      error.value =
        cause.status >= 500
          ? 'Dịch vụ kích hoạt chưa sẵn sàng. Vui lòng thử lại sau.'
          : 'Không thể kích hoạt. Vui lòng kiểm tra liên kết và mật khẩu rồi thử lại.'
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story" aria-label="Company HRM">
      <div class="brand brand-inverse">
        <span class="brand-icon">C<span>h</span></span>
        <span>company<span class="brand-light">hrm</span></span>
      </div>
      <div class="login-story-content">
        <span class="story-eyebrow">PEOPLE. CONNECTED.</span>
        <h1>Chào mừng bạn<br />đến với đội ngũ.</h1>
        <p>Đặt mật khẩu để bắt đầu sử dụng không gian làm việc Company HRM của bạn.</p>
      </div>
      <div class="story-footer"><span>Company HRM</span><span>Built around people.</span></div>
    </section>
    <main class="login-main">
      <div class="login-mobile-brand brand">
        <span class="brand-icon">C<span>h</span></span
        ><span>Company HRM</span>
      </div>
      <div class="login-form-wrap">
        <span class="login-lock"><AppIcon :name="activated ? 'check' : 'lock'" :size="24" /></span>
        <p class="eyebrow">CHÀO MỪNG THÀNH VIÊN MỚI</p>
        <h2>{{ activated ? 'Kích hoạt thành công' : 'Kích hoạt tài khoản' }}</h2>
        <template v-if="activated">
          <p class="alert alert-success" role="status">
            Tài khoản đã được kích hoạt. Bạn có thể đăng nhập bằng email công việc và mật khẩu vừa
            đặt.
          </p>
          <RouterLink :to="{ name: 'login' }" class="button button-primary login-submit">
            Đi đến đăng nhập <AppIcon name="arrow" :size="18" />
          </RouterLink>
        </template>
        <template v-else>
          <p class="muted login-description">
            Đặt mật khẩu để hoàn tất kích hoạt tài khoản của bạn.
          </p>
          <div v-if="error" id="activation-error" class="alert alert-error" role="alert">
            {{ error }}
          </div>
          <form v-if="validToken" :aria-busy="busy" @submit.prevent="submit">
            <fieldset :disabled="busy" class="form-fields">
              <label for="activation-password">Mật khẩu mới</label>
              <div class="input-icon">
                <AppIcon name="lock" :size="18" />
                <input
                  id="activation-password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  minlength="12"
                  maxlength="72"
                  required
                  :aria-describedby="error ? 'password-help activation-error' : 'password-help'"
                />
                <button
                  type="button"
                  class="password-toggle"
                  :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                  :aria-pressed="showPassword"
                  @click="showPassword = !showPassword"
                >
                  <AppIcon name="eye" :size="18" />
                </button>
              </div>
              <p id="password-help" class="muted">Ít nhất 12 ký tự, tối đa 72 byte UTF-8.</p>
              <label for="activation-confirmation">Nhập lại mật khẩu</label>
              <div class="input-icon">
                <AppIcon name="lock" :size="18" />
                <input
                  id="activation-confirmation"
                  v-model="confirmation"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  maxlength="72"
                  required
                  :aria-describedby="error ? 'activation-error' : undefined"
                />
              </div>
              <button type="submit" class="button button-primary login-submit">
                {{ busy ? 'Đang kích hoạt…' : 'Đặt mật khẩu và kích hoạt' }}
              </button>
            </fieldset>
          </form>
          <p class="login-help">
            Đã kích hoạt tài khoản?
            <RouterLink :to="{ name: 'login' }">Đi đến đăng nhập</RouterLink>
          </p>
        </template>
      </div>
      <footer class="login-footer">© {{ new Date().getFullYear() }} Company HRM.</footer>
    </main>
  </div>
</template>
