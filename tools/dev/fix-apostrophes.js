// Echappe les apostrophes non echappees dans les fichiers strings.xml (aapt les refuse).
// Usage : node tools/dev/fix-apostrophes.js
'use strict';
const fs = require('fs');
const path = require('path');

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'build' || e.name === 'node_modules' || e.name.startsWith('.')) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (/^(strings|familles)\.xml$/.test(e.name) && p.includes(path.join('res', 'values'))) out.push(p);
  }
  return out;
}

let changed = 0;
for (const f of walk(path.join(__dirname, '..', '..'), [])) {
  const src = fs.readFileSync(f, 'utf8');
  const fixed = src.replace(/(<(?:string|item)\b[^>]*>)([\s\S]*?)(<\/(?:string|item)>)/g, (m, open, body, close) =>
    open + body.replace(/(^|[^\\])'/g, "$1\\'").replace(/(^|[^\\])'/g, "$1\\'") + close);
  if (fixed !== src) {
    fs.writeFileSync(f, fixed);
    changed++;
    console.log('corrige :', path.relative(process.cwd(), f));
  }
}
console.log(changed ? `${changed} fichier(s) corrige(s)` : 'rien a corriger');
