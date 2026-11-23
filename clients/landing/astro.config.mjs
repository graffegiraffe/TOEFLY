// @ts-check
import sitemap from '@astrojs/sitemap';
import { defineConfig, envField } from 'astro/config';
import { existsSync } from 'node:fs';

// `site` is needed at config time for canonical URLs and the sitemap, so it is read from the environment directly.
if (existsSync('.env')) process.loadEnvFile('.env');
const { SITE_URL } = process.env;

export default defineConfig({
  site: SITE_URL || 'http://localhost:4321',
  output: 'static',
  trailingSlash: 'never',
  build: { format: 'file', inlineStylesheets: 'always' },
  integrations: [sitemap()],
  env: {
    schema: {
      APP_URL: envField.string({
        context: 'server',
        access: 'public',
        default: 'http://localhost:5173',
        url: true,
      }),
      CONTACT_EMAIL: envField.string({ context: 'server', access: 'public', optional: true }),
    },
  },
});
