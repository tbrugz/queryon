// Builds each vendor entry as a classic (IIFE) script, so existing pages can keep using <script src> tags
import { build } from 'vite';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(here, '../webapp/js/vendor');
const cssOutDir = path.resolve(here, '../webapp/css/vendor');
const entries = ['jquery', 'd3', 'nvd3', 'prism'];

for (const [i, name] of entries.entries()) {
  await build({
    root: here,
    configFile: false,
    logLevel: 'warn',
    build: {
      outDir,
      emptyOutDir: i === 0,
      minify: true,
      lib: {
        entry: path.resolve(here, 'src', `${name}.js`),
        name: `qonVendor_${name}`,
        formats: ['iife'],
        fileName: () => `${name}.min.js`,
      },
    },
  });
}

// prism theme
fs.mkdirSync(cssOutDir, { recursive: true });
fs.copyFileSync(path.resolve(here, 'node_modules/prismjs/themes/prism-twilight.css'), path.join(cssOutDir, 'prism.css'));
