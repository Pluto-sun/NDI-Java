import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: process.env.NDI_FRONTEND_HOST ?? '0.0.0.0',
    port: 5173,
    strictPort: true,
    proxy: { '/api': process.env.NDI_DEV_API_TARGET ?? 'http://127.0.0.1:8080' },
  },
});
