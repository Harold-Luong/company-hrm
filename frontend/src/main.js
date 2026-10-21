import { createApp } from 'vue'
import './assets/styles/main.css'
import './assets/styles/attendance.css'
import App from './App.vue'
import router from './router/index.js'
import i18n, { startLocaleSync } from './i18n/index.js'
const stopLocaleSync = startLocaleSync()
if (import.meta.hot) import.meta.hot.dispose(stopLocaleSync)
const app = createApp(App)
app.use(i18n)
app.use(router)
app.mount('#app')
