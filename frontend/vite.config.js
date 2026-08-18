import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 本地开发时把 API 转发到 Spring Boot，避免浏览器跨域问题。
    proxy: {
      '/api': 'http://localhost:8081',
      '/user': 'http://localhost:8081'
    }
  }
})
