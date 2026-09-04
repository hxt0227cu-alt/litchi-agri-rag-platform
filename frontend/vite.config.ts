import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [
        ElementPlusResolver({ importStyle: false })
      ],
      dts: false
    })
  ],
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

          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vendor-vue'
          }

          if (id.includes('three') || id.includes('@tweenjs') || id.includes('three/examples')) {
            return 'vendor-three'
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
  },
  preview: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
