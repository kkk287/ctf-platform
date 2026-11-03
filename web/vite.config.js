import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 三入口多页面构建：选手端 / 管理端 / 大屏端
// 产物输出到后端 static/vue，随 jar 一起打包；原生页面保留在 static 根目录作为回退
// Element Plus 按需引入：模板组件自动注册，ElMessage 等 API 自动导入，样式按组件注入
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  base: '/vue/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: '../src/main/resources/static/vue',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      input: {
        player: fileURLToPath(new URL('./player.html', import.meta.url)),
        admin: fileURLToPath(new URL('./admin.html', import.meta.url)),
        screen: fileURLToPath(new URL('./screen.html', import.meta.url))
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
