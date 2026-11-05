import tailwindcss from '@tailwindcss/vite';
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Locally the app runs on app.toefly.localhost and the gateway on api.toefly.localhost: one site, two origins,
// so the SameSite=Strict session cookie reaches the gateway (docs/10-security-privacy.md §4.2).
export default defineConfig({
  plugins: [tanstackRouter({ target: 'react', autoCodeSplitting: true }), react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { host: 'app.toefly.localhost', port: 5173, strictPort: true, allowedHosts: ['app.toefly.localhost'] },
  preview: { host: 'app.toefly.localhost', port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', 'src/routeTree.gen.ts', 'src/main.tsx', 'src/test/**'],
    },
  },
});
