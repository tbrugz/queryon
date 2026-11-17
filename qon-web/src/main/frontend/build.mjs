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

// font-awesome 4.x (css + fonts, keeping its relative css/ and fonts/ layout)
const faSrc = path.resolve(here, 'node_modules/font-awesome');
const faOut = path.join(cssOutDir, 'font-awesome');
fs.mkdirSync(path.join(faOut, 'css'), { recursive: true });
fs.mkdirSync(path.join(faOut, 'fonts'), { recursive: true });
fs.copyFileSync(path.join(faSrc, 'css/font-awesome.min.css'), path.join(faOut, 'css/font-awesome.min.css'));
for (const f of ['fontawesome-webfont.woff', 'fontawesome-webfont.woff2']) {
  fs.copyFileSync(path.join(faSrc, 'fonts', f), path.join(faOut, 'fonts', f));
}

// sigma.js 1.2.1 (classic scripts, copied as is) + plugins
const sigmaSrc = path.resolve(here, 'node_modules/sigma/build');
const sigmaOut = path.join(outDir, 'sigma');
fs.mkdirSync(path.join(sigmaOut, 'plugins'), { recursive: true });
fs.copyFileSync(path.join(sigmaSrc, 'sigma.min.js'), path.join(sigmaOut, 'sigma.min.js'));
for (const f of ['sigma.layout.forceAtlas2.min.js', 'sigma.plugins.dragNodes.min.js']) {
  fs.copyFileSync(path.join(sigmaSrc, 'plugins', f), path.join(sigmaOut, 'plugins', f));
}
