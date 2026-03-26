import App from './App.vue'
import './uni.promisify.adaptor'

import { createSSRApp } from 'vue'
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
