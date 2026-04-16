import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 3000,
    strictPort: false,
    host: '0.0.0.0'
  }
})
