import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'
import { useAppStore } from './store/app'
import './assets/styles/index.css'

/**
 * 应用入口。
 *
 * <p>Element Plus 采用全量注册：后台管理对体积不敏感，换来的是
 * 页面里不用逐个 import 组件（也少一类“忘记注册导致组件不渲染”的坑）。
 * 后续要优化首屏体积时，再换 unplugin-vue-components 按需引入。</p>
 *
 * <p>暗色样式表必须显式引入：Element Plus 2.x 的 dark/css-vars.css
 * 只在 html.dark 下生效，不引就会“背景黑了、组件还是白的”。</p>
 */
const app = createApp(App)
const pinia = createPinia()

for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(pinia)
// 先落主题再挂载，避免刷新时先闪一下亮色
useAppStore(pinia).initTheme()
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.directive('hasPerm', permission)

app.mount('#app')
