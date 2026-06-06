import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react({jsxRuntime: 'automatic'})],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/ws/gateway': {target: 'ws://localhost:8080', ws: true},
      '/api': {target: 'http://localhost:8080', changeOrigin: true},
    },
  },
})
