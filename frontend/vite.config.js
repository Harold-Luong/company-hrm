import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api/v1/employees': {
          target: env.EMPLOYEE_API_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
        '/api/v1/departments': {
          target: env.EMPLOYEE_API_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
        '/api/v1/positions': {
          target: env.EMPLOYEE_API_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
        '/api': {
          target: env.API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
