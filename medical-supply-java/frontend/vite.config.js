import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: path.resolve(here, '../src/main/resources/web'),
    emptyOutDir: true,
    assetsInlineLimit: 4096,
    target: ['chrome100', 'edge100'],
    sourcemap: false
  }
})
