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

// nvd3 css (same version as the nvd3 js bundle)
fs.copyFileSync(path.resolve(here, 'node_modules/nvd3/build/nv.d3.min.css'), path.join(cssOutDir, 'nv.d3.min.css'));

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

// ace editor (noconflict, minified) + QueryOn's sql mode; only the files used by the pages are copied
const aceSrc = path.resolve(here, 'node_modules/ace-builds/src-min-noconflict');
const aceOut = path.join(outDir, 'ace');
fs.mkdirSync(aceOut, { recursive: true });
const aceModes = ['css', 'diff', 'html', 'javascript', 'json', 'markdown', 'plain_text', 'sql', 'xml'];
// modes with syntax checking start a web worker (worker-<mode>.js), loaded lazily by ace
const aceWorkers = ['css', 'html', 'javascript', 'json', 'xml'];
const aceFiles = ['ace.js', 'ext-searchbox.js', 'theme-twilight.js']
  .concat(aceModes.map(m => `mode-${m}.js`))
  .concat(aceWorkers.map(w => `worker-${w}.js`));
for (const f of aceFiles) {
  fs.copyFileSync(path.join(aceSrc, f), path.join(aceOut, f));
}
// ace only loads the requested mode file (not its dependencies), so upstream mode-sql is bundled into mode-qon_sql
fs.writeFileSync(path.join(aceOut, 'mode-qon_sql.js'),
  fs.readFileSync(path.join(aceSrc, 'mode-sql.js'), 'utf8') + '\n;\n' + fs.readFileSync(path.resolve(here, 'ace/mode-qon_sql.js'), 'utf8'));
