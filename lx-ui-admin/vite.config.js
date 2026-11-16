import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 凌霄后台前端构建配置。
 *
 * 代理说明：前端所有请求都以 /api 开头（与后端 Controller 路径一致），
 * 开发期由 Vite 转发到 8080，生产期由 Nginx 转发，前端代码不需要区分环境。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT || 5173),
      open: false,
      // 构建产物不参与热更新：dist 里的文件被外部进程（预览 / 缩略图缓存）占用时，
      // chokidar 读取会抛 EBUSY，直接把 dev server 带崩。
      watch: {
        ignored: ['**/dist/**', '**/dist-*/**']
      },
      proxy: {
        '/api': {
          target,
          changeOrigin: true
        },
        // 接口文档：/doc.html 是 Knife4j 页面，/v3/api-docs 是 OpenAPI 描述，
        // /webjars 是它的静态资源。三者都不在 /api 下，开发期必须单独转发，
        // 否则 iframe 打开的是 Vite 的 404 页。
        '/doc.html': {
          target,
          changeOrigin: true
        },
        '/v3/api-docs': {
          target,
          changeOrigin: true
        },
        '/webjars': {
          target,
          changeOrigin: true
        },
        '/swagger-ui': {
          target,
          changeOrigin: true
        },
        '/favicon.ico': {
          target,
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500
    }
  }
})
