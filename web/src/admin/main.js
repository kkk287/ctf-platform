import { createApp } from 'vue'
// ElMessage / ElMessageBox 为显式导入的函数式 API，样式不在模板组件解析范围内，需单独引入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import '../shared/theme.css'
import App from './App.vue'
import router from './router'

// Element Plus 组件与样式由 unplugin-vue-components 按需注入（见 vite.config.js）
createApp(App).use(router).mount('#app')
