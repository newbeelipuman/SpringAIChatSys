import { createApp } from 'vue'
import { ElInput } from 'element-plus'
import 'element-plus/es/components/input/style/css'
import App from './ui/App.vue'
import { router } from './ui/router'
import './style.css'

createApp(App).use(router).component('ElInput', ElInput).mount('#app')
