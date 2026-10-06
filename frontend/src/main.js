import { createApp } from 'vue'
import './assets/styles/main.css'
import './assets/styles/attendance.css'
import App from './App.vue'
import router from './router/index.js'
createApp(App).use(router).mount('#app')
