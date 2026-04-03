import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 3000,       // 换成 3000 端口，避开 5173
    strictPort: false,
    host: '127.0.0.1'  // 关键！将 0.0.0.0 改为 127.0.0.1，这通常能跳过权限校验
  }
});