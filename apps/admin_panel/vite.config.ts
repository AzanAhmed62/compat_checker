import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// The admin console is a static SPA. It only ever receives the public anon key;
// authorization is enforced by RLS and server-side RPC checks.
export default defineConfig({
  plugins: [react()],
  build: {
    sourcemap: false,
    target: 'es2022',
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
