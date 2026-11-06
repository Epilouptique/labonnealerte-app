// Test hors ligne de la CREATION DE COMPTE par code (server/routes/app-api.js, APP-04).
// Vrais modules du site : app-api.js, sessions.js, token-hash.js, auth-transport.js.
// Simules : base (tables subscribers/sessions en memoire), e-mails, webpush, forum.
// Aucune base reelle, aucun appel a la production.
// Usage : node tools/server-tests/test-app-signup.js
'use strict';
const path = require('path');
const fs = require('fs');
const SRV = 'C:/Dev/Labonnealerte/server';
process.env.TOKEN_HASH_SECRET = 'secret-de-test-uniquement';
process.env.APP_CODE_MAX_PER_HOUR = '100000'; // plafond global hors sujet ici (voir test-app-mail-cap.js)
process.env.APP_CODE_MAX_PER_DAY = '100000';

const stub = (rel, exports) => {
  const file = require.resolve(path.join(SRV, rel));
  require.cache[file] = { id: file, filename: file, loaded: true, exports };
};

// --- Largeurs declarees dans init.sql (CREATE TABLE + ADD COLUMN + ALTER ... TYPE) ---
const initSql = fs.readFileSync(path.join(SRV, 'db/init.sql'), 'utf8');
function declaredWidths(table) {
  const widths = {};
  const create = initSql.match(new RegExp(`CREATE TABLE IF NOT EXISTS ${table} \\(([\\s\\S]*?)\\n\\);`));
  if (create) {
    for (const m of create[1].matchAll(/^\s*(\w+)\s+VARCHAR\((\d+)\)/gim)) widths[m[1]] = Math.max(widths[m[1]] || 0, +m[2]);
  }
  for (const m of initSql.matchAll(new RegExp(`ALTER TABLE ${table} ADD COLUMN IF NOT EXISTS (\\w+) VARCHAR\\((\\d+)\\)`, 'gi'))) {
    widths[m[1]] = Math.max(widths[m[1]] || 0, +m[2]);
  }
  for (const m of initSql.matchAll(new RegExp(`ALTER TABLE ${table} ALTER COLUMN (\\w+) TYPE VARCHAR\\((\\d+)\\)`, 'gi'))) {
    widths[m[1]] = Math.max(widths[m[1]] || 0, +m[2]);
  }
  return widths;
}
const WIDTHS = { subscribers: declaredWidths('subscribers'), sessions: declaredWidths('sessions') };
const writes = []; // { table, column, length }
const noteWrite = (table, column, value) => {
  if (typeof value === 'string') writes.push({ table, column, length: value.length });
};

// --- Base simulee ---
const db = { subscribers: [], sessions: [], nextId: 1 };
let insertDelayMs = 0;
let slowSelectMs = 0;
let subscriberWrites = 0; // INSERT/UPDATE sur subscribers (hors magic_token de connexion)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
stub('db', { pool: { query: async (sql, params) => {
  const q = sql.replace(/\s+/g, ' ').trim();
  if (q.startsWith('SELECT id FROM subscribers WHERE email = $1 AND confirmed = true')) {
    if (slowSelectMs) await sleep(slowSelectMs);
    return { rows: db.subscribers.filter((s) => s.email === params[0] && s.confirmed).map((s) => ({ id: s.id })) };
  }
  if (q.startsWith('UPDATE subscribers SET magic_token = $1')) {
    const s = db.subscribers.find((x) => x.id === params[1]);
    noteWrite('subscribers', 'magic_token', params[0]);
    s.magic_token = params[0]; s.magic_expires = Date.now() + 30 * 60_000;
    return { rows: [] };
  }
  if (q.startsWith('INSERT INTO subscribers (email, confirmed, token, terms_accepted_at, terms_version)')) {
    subscriberWrites++;
    if (insertDelayMs) await sleep(insertDelayMs);
    ['email', 'token', 'terms_version'].forEach((c, i) => noteWrite('subscribers', c, params[i]));
    let s = db.subscribers.find((x) => x.email === params[0]);
    if (s) { s.confirmed = true; s.terms_version = params[2]; s.terms_at = new Date(); } else {
      s = { id: db.nextId++, email: params[0], confirmed: true, token: params[1], terms_version: params[2], terms_at: new Date() };
      db.subscribers.push(s);
    }
    return { rows: [{ id: s.id, email: s.email }] };
  }
  if (q.startsWith('INSERT INTO sessions (token, subscriber_id, expires_at)')) {
    noteWrite('sessions', 'token', params[0]);
    db.sessions.push({ token: params[0], subscriber_id: params[1] });
    return { rows: [] };
  }
  if (q.includes('FROM sessions s JOIN subscribers subr')) {
    const sess = db.sessions.find((x) => params[0].includes(x.token));
    if (!sess) return { rows: [] };
    const sub = db.subscribers.find((x) => x.id === sess.subscriber_id);
    return { rows: [{ subscriber_id: sub.id, stored: sess.token, email: sub.email, needs_touch: false }] };
  }
  if (q.startsWith('SELECT id, email FROM subscribers WHERE magic_token = ANY')) {
    return { rows: db.subscribers.filter((s) => s.magic_token && params[0].includes(s.magic_token) && s.magic_expires > Date.now()).map((s) => ({ id: s.id, email: s.email })) };
  }
  if (q.startsWith('UPDATE subscribers SET magic_token = NULL')) {
    const s = db.subscribers.find((x) => x.id === params[0]); s.magic_token = null; return { rows: [] };
  }
  if (q.includes('FROM source_events')) return { rows: [] };
  throw new Error('requete non simulee : ' + q.slice(0, 80));
} } });

const mails = [];
stub('mailer', {
  sendAppLoginCode: async (email, token, code) => { mails.push({ kind: 'connexion', email, code }); },
  sendAppSignupCode: async (email, code) => { mails.push({ kind: 'creation', email, code }); },
});
stub('webpush', { publicKey: () => 'VAPIDPUB' });
stub('params', { resolveLabel: () => '' });
stub('routes/forum', { CATEGORIES: {} });
stub('public-events', { publicEventMessage: () => null });
const origError = console.error;
console.error = () => {};

const express = require(require.resolve('express', { paths: [SRV] }));
const app = express();
app.set('trust proxy', 1);
app.use(express.json());
app.use('/api', require(path.join(SRV, 'routes/app-api')));

const realNow = Date.now;
let offset = 0;
Date.now = () => realNow() + offset;

const srv = app.listen(0, async () => {
  const base = `http://127.0.0.1:${srv.address().port}/api`;
  let ip = 0;
  const post = (p, b) => fetch(base + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `10.9.${(ip >> 8) & 255}.${ip++ & 255}` },
    body: JSON.stringify(b),
  }).then(async (r) => ({ status: r.status, body: await r.json() }));
  const settle = () => sleep(40);
  const lastMail = (email) => mails.filter((m) => m.email === email).pop();
  let fails = 0;
  const ok = (c, msg) => { console.log((c ? 'OK   ' : 'ECHEC') + ' ' + msg); if (!c) { fails++; process.exitCode = 1; } };
  const cfg = await fetch(base + '/app/config').then((r) => r.json());
  const V = cfg.terms_version;
  ok(typeof V === 'string' && V.length <= WIDTHS.subscribers.terms_version && /^https:\/\//.test(cfg.terms_url), `config : version des conditions (${V}) et lien`);

  // Compte existant de reference
  db.subscribers.push({ id: db.nextId++, email: 'connu@b.fr', confirmed: true, token: 'x'.repeat(64) });

  // 1. Meme reponse (statut, corps, delai) connue / inconnue ; un code part dans les deux cas
  const beforeWrites = subscriberWrites;
  await post('/app/login/request', { email: 'chauffe@b.fr' }); await settle(); // ouvre la connexion HTTP
  const rKnown = await post('/app/login/request', { email: 'connu@b.fr' });
  const rNew = await post('/app/login/request', { email: 'Nouveau@B.fr ' });
  await settle();
  // Delai : base ralentie (300 ms) ; 5 mesures alternees sur d'autres adresses, medianes comparees.
  slowSelectMs = 300;
  const dK = []; const dN = [];
  for (let i = 0; i < 5; i++) {
    db.subscribers.push({ id: db.nextId++, email: `connu${i}@b.fr`, confirmed: true, token: 'z'.repeat(64) });
    let t = realNow(); await post('/app/login/request', { email: `connu${i}@b.fr` }); dK.push(realNow() - t);
    t = realNow(); await post('/app/login/request', { email: `inconnu${i}@b.fr` }); dN.push(realNow() - t);
  }
  await sleep(350); slowSelectMs = 0;
  const med = (a) => a.slice().sort((x, y) => x - y)[2];
  const dKnown = med(dK); const dNew = med(dN);
  ok(rKnown.status === rNew.status && JSON.stringify(rKnown.body) === JSON.stringify(rNew.body), 'meme statut et meme corps pour une adresse connue et inconnue');
  ok(dKnown < 100 && dNew < 100 && Math.abs(dKnown - dNew) < 30, `meme delai, sans attendre la base ralentie a 300 ms (medianes ${dKnown} ms / ${dNew} ms)`);
  ok(lastMail('connu@b.fr')?.kind === 'connexion' && lastMail('nouveau@b.fr')?.kind === 'creation', 'un code est envoye dans les deux cas (e-mail connexion / creation)');
  ok(subscriberWrites === beforeWrites && !db.subscribers.some((s) => s.email === 'nouveau@b.fr'), 'aucun abonne pre-cree a la demande');

  // 2. Mauvais code : meme reponse que pour un compte existant
  const wrong = (c) => (c === '000000' ? '111111' : '000000');
  const w1 = await post('/app/login/verify', { email: 'connu@b.fr', code: wrong(lastMail('connu@b.fr').code) });
  const w2 = await post('/app/login/verify', { email: 'nouveau@b.fr', code: wrong(lastMail('nouveau@b.fr').code) });
  ok(w1.status === 400 && w2.status === 400 && JSON.stringify(w1.body) === JSON.stringify(w2.body), 'mauvais code : meme reponse connue / inconnue');

  // 3. Bon code sans conditions : 409, code conserve, rien de cree
  const code = lastMail('nouveau@b.fr').code;
  let r = await post('/app/login/verify', { email: 'nouveau@b.fr', code });
  ok(r.status === 409 && r.body.needs_terms === true && r.body.terms_version === V, 'bon code sans conditions : 409 needs_terms');
  r = await post('/app/login/verify', { email: 'nouveau@b.fr', code, accept_terms: true, terms_version: 'ancienne' });
  ok(r.status === 409 && r.body.needs_terms === true, 'version des conditions perimee : 409');
  r = await post('/app/login/verify', { email: 'nouveau@b.fr', code, accept_terms: 'true', terms_version: V });
  ok(r.status === 409, 'accept_terms doit etre le booleen true');
  ok(!db.subscribers.some((s) => s.email === 'nouveau@b.fr'), 'toujours aucun compte sans acceptation');

  // 4. Acceptation : compte cree, session utilisable, conditions enregistrees
  r = await post('/app/login/verify', { email: 'nouveau@b.fr', code, accept_terms: true, terms_version: V });
  const sub = db.subscribers.find((s) => s.email === 'nouveau@b.fr');
  ok(r.status === 200 && r.body.created === true && /^[0-9a-f]{64}$/.test(r.body.token) && r.body.email === 'nouveau@b.fr', 'creation : 200, jeton brut de 64 hex rendu');
  ok(sub && sub.confirmed && sub.terms_version === V && sub.terms_at instanceof Date, 'compte confirme, date et version des conditions enregistrees');
  ok(db.sessions.length === 1 && db.sessions[0].token.startsWith('h1:') && db.sessions[0].token.length === 67, 'session stockee en EMPREINTE h1: (67 car.), jamais le brut');
  const ev = await fetch(base + '/app/events', { headers: { Authorization: 'Bearer ' + r.body.token } });
  ok(ev.status === 200, 'la session creee ouvre les routes authentifiees');

  // 5. Usage unique
  r = await post('/app/login/verify', { email: 'nouveau@b.fr', code, accept_terms: true, terms_version: V });
  ok(r.status === 400, 'code a usage unique');

  // 6. Expiration (30 min)
  await post('/app/login/request', { email: 'expire@b.fr' }); await settle();
  const cExp = lastMail('expire@b.fr').code;
  offset = 31 * 60_000;
  r = await post('/app/login/verify', { email: 'expire@b.fr', code: cExp, accept_terms: true, terms_version: V });
  offset = 0;
  ok(r.status === 400 && !db.subscribers.some((s) => s.email === 'expire@b.fr'), 'code expire apres 30 min : refuse, rien de cree');

  // 7. Limites identiques a la connexion
  mails.length = 0;
  for (let i = 0; i < 4; i++) { await post('/app/login/request', { email: 'plafond@b.fr' }); await settle(); }
  ok(mails.filter((m) => m.email === 'plafond@b.fr').length === 3, '3 envois max par 15 min pour une adresse inconnue');
  const cTries = lastMail('plafond@b.fr').code;
  for (let i = 0; i < 5; i++) await post('/app/login/verify', { email: 'plafond@b.fr', code: wrong(cTries) });
  r = await post('/app/login/verify', { email: 'plafond@b.fr', code: cTries, accept_terms: true, terms_version: V });
  ok(r.status === 400 && !db.subscribers.some((s) => s.email === 'plafond@b.fr'), '5 essais max par code, puis code invalide');
  for (let i = 0; i < 6; i++) r = await post('/app/login/verify', { email: 'plafond@b.fr', code: '123456' });
  ok(r.status === 429, 'adresse inconnue verrouillee apres 10 echecs, comme une connue');
  let codesIp = [];
  for (let i = 0; i < 12; i++) {
    const x = await fetch(base + '/app/login/verify', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '10.200.0.1' }, body: JSON.stringify({ email: `ip${i}@b.fr`, code: '123456' }) });
    codesIp.push(x.status);
  }
  ok(codesIp.slice(10).every((s) => s === 429), 'limite par IP (10 / min) inchangee');

  // 8. Concurrence : deux validations simultanees du meme code -> un seul compte
  await post('/app/login/request', { email: 'course@b.fr' }); await settle();
  const cRace = lastMail('course@b.fr').code;
  insertDelayMs = 150;
  const before = subscriberWrites;
  const [a, b] = await Promise.all([
    post('/app/login/verify', { email: 'course@b.fr', code: cRace, accept_terms: true, terms_version: V }),
    post('/app/login/verify', { email: 'course@b.fr', code: cRace, accept_terms: true, terms_version: V }),
  ]);
  insertDelayMs = 0;
  ok([a.status, b.status].sort().join() === '200,400' && subscriberWrites - before === 1 && db.subscribers.filter((s) => s.email === 'course@b.fr').length === 1,
    `concurrence : une seule creation (${a.status}/${b.status}, ${subscriberWrites - before} INSERT)`);

  // 9. Ligne non confirmee du site : confirmee, pas de doublon
  db.subscribers.push({ id: db.nextId++, email: 'attente@b.fr', confirmed: false, token: 'y'.repeat(64) });
  await post('/app/login/request', { email: 'attente@b.fr' }); await settle();
  ok(lastMail('attente@b.fr').kind === 'creation', 'adresse inscrite sur le site mais jamais confirmee : e-mail de creation');
  r = await post('/app/login/verify', { email: 'attente@b.fr', code: lastMail('attente@b.fr').code, accept_terms: true, terms_version: V });
  const rows = db.subscribers.filter((s) => s.email === 'attente@b.fr');
  ok(r.status === 200 && rows.length === 1 && rows[0].confirmed && rows[0].token === 'y'.repeat(64), 'ligne existante confirmee, meme id et meme jeton d\'abonne, sans doublon');

  // 10. Connexion d'un compte existant inchangee (pas de conditions demandees)
  await post('/app/login/request', { email: 'connu@b.fr' }); await settle();
  r = await post('/app/login/verify', { email: 'connu@b.fr', code: lastMail('connu@b.fr').code });
  ok(r.status === 200 && !('created' in r.body), 'connexion d\'un compte existant : sans conditions, comme avant');

  // 11. Longueurs ecrites <= largeurs declarees dans init.sql
  const tooLong = writes.filter((w) => WIDTHS[w.table][w.column] !== undefined && w.length > WIDTHS[w.table][w.column]);
  const unknown = [...new Set(writes.filter((w) => WIDTHS[w.table][w.column] === undefined && w.column !== 'email').map((w) => `${w.table}.${w.column}`))];
  ok(writes.length > 0 && tooLong.length === 0 && unknown.length === 0,
    `largeurs init.sql respectees (${writes.length} ecritures ; sessions.token<=${WIDTHS.sessions.token}, magic_token<=${WIDTHS.subscribers.magic_token}, terms_version<=${WIDTHS.subscribers.terms_version})` +
    (tooLong.length ? ' TROP LONG : ' + JSON.stringify(tooLong[0]) : '') + (unknown.length ? ' NON DECLAREES : ' + unknown : ''));
  ok(WIDTHS.subscribers.email >= 254, `subscribers.email accepte 254 caracteres (VARCHAR(${WIDTHS.subscribers.email}))`);

  console.error = origError;
  Date.now = realNow;
  console.log(fails ? `${fails} echec(s)` : 'Tous les tests passent');
  srv.close();
});
