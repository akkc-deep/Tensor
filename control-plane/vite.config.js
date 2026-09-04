import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const backendUrl = process.env.TENSOR_BACKEND_URL || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
    },
  },
})
