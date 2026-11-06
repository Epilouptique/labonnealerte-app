// Test hors ligne du PLAFOND GLOBAL des e-mails de code (server/routes/app-api.js).
// Plafonds reduits par variables (4/h, 6/24 h). Base, e-mails, webpush simules.
// Aucune base reelle, aucun appel a la production.
// Usage : node tools/server-tests/test-app-mail-cap.js
'use strict';
const path = require('path');
const SRV = 'C:/Dev/Labonnealerte/server';
process.env.TOKEN_HASH_SECRET = 'secret-de-test-uniquement';
process.env.APP_CODE_MAX_PER_HOUR = '4';
process.env.APP_CODE_MAX_PER_DAY = '6';

const stub = (rel, exports) => {
  const file = require.resolve(path.join(SRV, rel));
  require.cache[file] = { id: file, filename: file, loaded: true, exports };
};
let selects = 0;
stub('db', { pool: { query: async (sql) => {
  const q = sql.replace(/\s+/g, ' ').trim();
  if (q.startsWith('SELECT id FROM subscribers WHERE email = $1 AND confirmed = true')) { selects++; return { rows: [] }; }
  throw new Error('requete non simulee : ' + q.slice(0, 80));
} } });
const mails = [];
stub('mailer', {
  sendAppLoginCode: async (email) => { mails.push(email); },
  sendAppSignupCode: async (email) => { mails.push(email); },
});
stub('webpush', { publicKey: () => 'VAPIDPUB' });
stub('params', { resolveLabel: () => '' });
stub('routes/forum', { CATEGORIES: {} });
stub('public-events', { publicEventMessage: () => null });
const warns = [];
console.warn = (...a) => warns.push(a.join(' '));
console.error = () => {};

const express = require(require.resolve('express', { paths: [SRV] }));
const APP_API = require.resolve(path.join(SRV, 'routes/app-api'));
function mount() {
  delete require.cache[APP_API]; // simule un redemarrage : module recharge, compteurs neufs
  const app = express();
  app.set('trust proxy', 1);
  app.use(express.json());
  app.use('/api', require(APP_API));
  return app;
}

const realNow = Date.now;
let offset = 0;
Date.now = () => realNow() + offset;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let fails = 0;
const ok = (c, msg) => { console.log((c ? 'OK   ' : 'ECHEC') + ' ' + msg); if (!c) { fails++; process.exitCode = 1; } };

async function withServer(app, fn) {
  const srv = app.listen(0);
  await new Promise((r) => srv.once('listening', r));
  const base = `http://127.0.0.1:${srv.address().port}/api`;
  try { await fn(base); } finally { srv.close(); }
}

let ip = 0;
const request = (base, email) => fetch(base + '/app/login/request', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `10.7.${(ip >> 8) & 255}.${ip++ & 255}` },
  body: JSON.stringify({ email }),
}).then(async (r) => ({ status: r.status, text: await r.text() }));

(async () => {
  let n = 0;
  const fresh = () => `adresse${n++}@test.fr`; // une adresse et une IP neuves a chaque fois
  await withServer(mount(), async (base) => {
    const first = await request(base, fresh());
    await sleep(30);
    for (let i = 0; i < 3; i++) await request(base, fresh());
    await sleep(30);
    ok(mails.length === 4, `4 e-mails envoyes sous le plafond horaire (${mails.length})`);

    const capped = await request(base, fresh());
    await sleep(30);
    ok(capped.status === first.status && capped.text === first.text, 'au plafond horaire : statut et corps identiques');
    ok(mails.length === 4, 'au plafond horaire : aucun e-mail de plus');
    const selectsAtCap = selects;
    await request(base, fresh()); await sleep(30);
    ok(selects === selectsAtCap, 'au plafond : aucun acces a la base (aucun code cree)');
    ok(warns.filter((w) => w.includes('PLAFOND GLOBAL ATTEINT (4/h)')).length === 1, 'une seule ligne de journal par fenetre horaire');
    ok(warns.every((w) => !w.includes('@')), 'journal sans adresse e-mail');

    // Verification au plafond : un code inconnu reste refuse (rien n'a ete cree).
    const v = await fetch(base + '/app/login/verify', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.8.0.1' },
      body: JSON.stringify({ email: `adresse${n - 1}@test.fr`, code: '000000' }),
    });
    ok(v.status === 400, `code demande au plafond : aucun code valide (statut ${v.status})`);

    // Une heure plus tard : de nouveau 2 envois possibles, puis plafond du jour (6/24 h).
    offset += 61 * 60_000;
    for (let i = 0; i < 3; i++) await request(base, fresh());
    await sleep(30);
    ok(mails.length === 6, `fenetre horaire suivante : plafonne a 6 par 24 h (${mails.length})`);
    ok(warns.some((w) => w.includes('PLAFOND GLOBAL ATTEINT (6/24 h)')), 'journal du plafond quotidien');

    // 25 h apres le premier envoi : le plafond quotidien se libere.
    offset += 24 * 60 * 60_000;
    await request(base, fresh()); await sleep(30);
    ok(mails.length === 7, 'apres 24 h glissantes : envoi de nouveau possible');
  });

  // Redemarrage : compteurs en memoire remis a zero (comportement documente).
  mails.length = 0;
  await withServer(mount(), async (base) => {
    for (let i = 0; i < 4; i++) await request(base, fresh());
    await sleep(30);
    ok(mails.length === 4, 'apres redemarrage : compteurs neufs, plafond horaire de nouveau applique a partir de zero');
    await request(base, fresh()); await sleep(30);
    ok(mails.length === 4, 'apres redemarrage : le plafond s\'applique toujours');
  });

  // Variables absentes ou invalides : valeurs par defaut.
  process.env.APP_CODE_MAX_PER_HOUR = 'abc';
  delete process.env.APP_CODE_MAX_PER_DAY;
  mails.length = 0;
  await withServer(mount(), async (base) => {
    for (let i = 0; i < 31; i++) await request(base, fresh());
    await sleep(60);
    ok(mails.length === 30, `variable invalide : defaut 30/h (${mails.length})`);
  });

  console.log(fails ? `\n${fails} echec(s)` : '\nTous les cas passent.');
  process.exit(process.exitCode || 0);
})();
