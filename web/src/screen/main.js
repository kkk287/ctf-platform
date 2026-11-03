import { createApp } from 'vue'
import '../shared/theme.css'
import App from './App.vue'

// Element Plus 组件与样式由 unplugin-vue-components 按需注入（见 vite.config.js）
createApp(App).mount('#app')
