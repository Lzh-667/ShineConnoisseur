import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'

const app = createApp(App)

app.use(router)
app.use(pinia)
app.use(ElementPlus)

// 暴露 router 实例，避免 request.js 中的循环依赖
window.__router = router

app.mount('#app')
