import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js'
import 'material-icons/iconfont/material-icons.css'
import './assets/style.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
