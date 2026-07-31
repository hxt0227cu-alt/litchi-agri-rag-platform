import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return
          }

          if (id.includes('element-plus')) {
            return 'vendor-element-plus'
          }

          if (id.includes('@element-plus/icons-vue')) {
            return 'vendor-element-icons'
          }

          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vendor-vue'
          }

          if (id.includes('axios')) {
            return 'vendor-axios'
          }

          if (id.includes('@vueuse')) {
            return 'vendor-vueuse'
          }

          if (id.includes('dayjs')) {
            return 'vendor-dayjs'
          }

          if (id.includes('async-validator')) {
            return 'vendor-async-validator'
          }

          if (id.includes('@floating-ui')) {
            return 'vendor-floating-ui'
          }

          if (id.includes('lodash-unified') || id.includes('/lodash/')) {
            return 'vendor-lodash'
          }
        }
      }
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
