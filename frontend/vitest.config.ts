import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// Unit and component tests run in a simulated browser (jsdom); no backend is needed because
// every test supplies its own fake fetch.
export default mergeConfig(viteConfig, defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
  },
}))
