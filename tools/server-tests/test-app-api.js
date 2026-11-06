// Test hors ligne de server/routes/app-api.js (site) : db, sessions, mailer, webpush simules,
// serveur Express en memoire sur un port local. Aucune base, aucun appel a la production.
// Usage : node tools/server-tests/test-app-api.js
const path = require('path');
const SRV = 'C:/Dev/Labonnealerte/server';
process.env.TOKEN_HASH_SECRET = process.env.TOKEN_HASH_SECRET || 'secret-de-test-uniquement'; // requis par token-hash.js
process.env.APP_CODE_MAX_PER_HOUR = '100000'; // plafond global hors sujet ici (voir test-app-mail-cap.js)
process.env.APP_CODE_MAX_PER_DAY = '100000';
const stub = (rel, exports) => {
  const file = require.resolve(path.join(SRV, rel));
  require.cache[file] = { id: file, filename: file, loaded: true, exports };
};
let sentCode = null;
let sentCodes = [];
let mailerFails = false;
let consumed = 0;
let dbDelayKnown = 0;
const queries = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
stub('db', { pool: { query: async (sql, params) => {
  queries.push({ sql: sql.replace(/\s+/g, ' '), params });
  if (sql.includes('SELECT id FROM subscribers')) return { rows: ['a@b.fr', 'b@b.fr', 'c@b.fr', 'd@b.fr'].includes(params[0]) ? [{ id: 7 }] : [] };
  if (sql.includes('UPDATE subscribers')) { await sleep(dbDelayKnown); return { rows: [] }; }
  if (sql.includes('GROUP BY category')) return { rows: [{ category: 'entraide', n: 4 }] };
  if (sql.includes('FROM forum_posts')) return { rows: [{ id: 5, body: 'Bonjour @hugo', created_at: new Date(), author: 'Hugo', author_pseudo: 'hugo' }] };
  if (sql.includes('WHERE ft.slug = $1')) return { rows: params[0] === 'cache' ? [{ id: 9, hidden: true }] : params[0] === 'ok' ? [{ id: 3, slug: 'ok', title: 'T', category: 'entraide', reply_count: 1, last_reply_at: new Date(), locked: false, hidden: false, author: 'Hugo', author_pseudo: 'hugo', email: 'fuite@x.fr' }] : [] };
  if (sql.includes('FROM forum_topics ft')) return { rows: Array.from({ length: 31 }, (_, i) => ({ id: i, slug: 's' + i, title: 't', category: 'entraide', reply_count: 0, last_reply_at: new Date(), locked: false, author: 'M' })) };
  if (sql.includes('FROM subscribers WHERE pseudo')) return { rows: params[0] === 'hugo' ? [{ display_name: 'Hugo', pseudo: 'hugo' }] : [] };
  if (sql.includes('FROM source_events')) {
    return { rows: [
      { id: 11, event: 'activated', message: '⛈️ Vigilance', created_at: new Date('2026-10-01T10:00:00Z'), params: { departement: '05' }, source_id: 'vigilance-meteo', source_name: 'Vigilance météo', params_schema: [], categories: ['meteo'] },
      { id: 12, event: 'deactivated', message: null, created_at: new Date('2026-10-02T10:00:00Z'), params: null, source_id: 'x', source_name: 'X', params_schema: null, categories: [] },
    ] };
  }
  return { rows: [] };
} } });
stub('sessions', { authenticate: async (t) => {
  const tok = typeof t === 'string' ? t : (t.headers.authorization || '').replace('Bearer ', '');
  if (tok === 'SESSION') return { id: 7, email: 'a@b.fr', sessionToken: 'SESSION' };
  if (tok && tok.length === 64 && consumed < 3) { consumed++; return { id: 7, email: 'a@b.fr', sessionToken: 'NEWSESSION' }; }
  return null;
} });
let signupCodes = 0;
stub('mailer', { sendAppSignupCode: async () => { signupCodes++; }, sendAppLoginCode: async (e, t, c) => {
  if (mailerFails) { const err = new Error(`550 Recipient address rejected: ${e}`); err.code = 'EENVELOPE'; throw err; }
  sentCode = c; sentCodes.push(c);
} });
stub('webpush', { publicKey: () => 'VAPIDPUB' });
stub('params', { resolveLabel: () => 'Hautes-Alpes' });
stub('routes/forum', { CATEGORIES: { discussion: 'Discussion libre', entraide: 'Entraide' } });
stub('public-events', { publicEventMessage: (e, m) => (e === 'activated' ? m : null) });

// Journaux captures : aucune adresse e-mail ne doit y apparaitre.
const logs = [];
const origError = console.error;
console.error = (...a) => logs.push(a.join(' '));

const express = require(require.resolve('express', { paths: [SRV] }));
const app = express();
app.set('trust proxy', 1); // comme server/index.js
app.use(express.json());
app.use('/api', require(path.join(SRV, 'routes/app-api')));
const srv = app.listen(0, async () => {
  const base = `http://127.0.0.1:${srv.address().port}/api`;
  // Chaque appel vient d'une « IP » distincte (dernier X-Forwarded-For, comme derriere Railway)
  // sauf si ip est fourni : les limites par IP ne parasitent pas les autres tests.
  let ipSeq = 0;
  const post = (p, b, ip, extra = {}) => fetch(base + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ip || `9.9.${(ipSeq >> 8) & 255}.${ipSeq++ & 255}`, ...extra },
    body: typeof b === 'string' ? b : JSON.stringify(b),
  });
  const settle = () => sleep(30); // l'envoi se fait apres la reponse neutre
  let fails = 0;
  const ok = (cond, msg) => { console.log((cond ? 'OK   ' : 'ECHEC') + ' ' + msg); if (!cond) { fails++; process.exitCode = 1; } };
  const wrong = (c) => (c === '000000' ? '111111' : '000000');

  let r = await fetch(base + '/app/config'); let j = await r.json();
  ok(r.status === 200 && j.min_version_code === 0 && j.vapid_public_key === 'VAPIDPUB', 'config');

  // --- Demande de code : neutralite, anti-enumeration ---
  r = await post('/app/login/request', { email: 'inconnu@b.fr' }); const jUnknown = await r.json(); await settle();
  ok(r.status === 200 && sentCode === null && signupCodes === 1, 'email inconnu : reponse neutre, code de CREATION envoye (APP-04), pas de code de connexion');
  r = await post('/app/login/request', { email: 'A@B.fr' }); const jKnown = await r.json(); await settle();
  ok(r.status === 200 && /^\d{6}$/.test(sentCode), 'email connu : code a 6 chiffres envoye');
  ok(JSON.stringify(jUnknown) === JSON.stringify(jKnown), 'meme corps de reponse inscrit / inconnu');
  dbDelayKnown = 400;
  let t0 = Date.now(); await post('/app/login/request', { email: 'b@b.fr' }); const tKnown = Date.now() - t0;
  t0 = Date.now(); await post('/app/login/request', { email: 'z@b.fr' }); const tUnknown = Date.now() - t0;
  ok(tKnown < 200 && tUnknown < 200, `reponse avant tout acces base (inscrit ${tKnown} ms, inconnu ${tUnknown} ms, base ralentie a 400 ms)`);
  await sleep(450); dbDelayKnown = 0;
  for (const bad of ['', 'pas-un-email', 'a'.repeat(250) + '@b.fr', 42, ['a@b.fr'], { $ne: 1 }]) {
    r = await post('/app/login/request', { email: bad }); ok(r.status === 200, `demande avec email invalide ${JSON.stringify(bad).slice(0, 20)} : neutre`);
  }
  r = await post('/app/login/request', '{pas du json'); ok(r.status === 400 || r.status === 200, 'corps JSON invalide : pas de plantage (' + r.status + ')');

  // --- Entropie : codes tires au hasard sur tout l'intervalle ---
  sentCodes = [];
  for (let i = 0; i < 3; i++) { await post('/app/login/request', { email: 'c@b.fr' }); await settle(); }
  ok(new Set(sentCodes).size === 3 && sentCodes.every((c) => /^\d{6}$/.test(c)), 'trois codes successifs distincts (crypto.randomInt)');

  // --- Verification ---
  await post('/app/login/request', { email: 'a@b.fr' }); await settle();
  r = await post('/app/login/verify', { email: 'a@b.fr', code: wrong(sentCode) }); j = await r.json();
  const jWrong = j;
  ok(r.status === 400, 'mauvais code refuse');
  r = await post('/app/login/verify', { email: 'personne@b.fr', code: '123456' }); j = await r.json();
  ok(r.status === 400 && JSON.stringify(j) === JSON.stringify(jWrong), 'email sans code : meme reponse que mauvais code (pas d\'enumeration)');
  for (const [body, label] of [[{ email: 'a@b.fr', code: 'abc' }, 'lettres'], [{ email: 'a@b.fr', code: 123456 }, 'nombre'], [{ email: 'a@b.fr', code: '1234567' }, '7 chiffres'], [{ email: ['a@b.fr'], code: '123456' }, 'email tableau'], [{ code: '123456' }, 'sans email']]) {
    r = await post('/app/login/verify', body); ok(r.status === 400, `code/email malforme (${label}) : 400`);
  }
  r = await post('/app/login/verify', { email: 'a@b.fr', code: sentCode }); j = await r.json();
  ok(r.status === 200 && j.token === 'NEWSESSION', 'bon code : session');
  r = await post('/app/login/verify', { email: 'a@b.fr', code: sentCode });
  ok(r.status === 400, 'code a usage unique');

  // 5 essais max par code, et le compteur survit a une nouvelle demande
  await post('/app/login/request', { email: 'b@b.fr' }); await settle();
  for (let i = 0; i < 4; i++) await post('/app/login/verify', { email: 'b@b.fr', code: wrong(sentCode) });
  await post('/app/login/request', { email: 'b@b.fr' }); await settle();
  const good = sentCode;
  await post('/app/login/verify', { email: 'b@b.fr', code: wrong(good) });
  r = await post('/app/login/verify', { email: 'b@b.fr', code: good });
  ok(r.status === 400, 'code invalide apres 5 erreurs, meme si un nouveau code a ete demande entre-temps');

  // Plafond d'envois : 3 / 15 min par email (b@b.fr a deja 3 envois)
  sentCode = null; await post('/app/login/request', { email: 'b@b.fr' }); await settle();
  ok(sentCode === null, '4e envoi en 15 min bloque (reponse neutre)');

  // Verrou par email : 10 echecs / h, quelle que soit l'IP
  let last;
  for (let i = 0; i < 12; i++) last = await post('/app/login/verify', { email: ' C@B.fr ', code: '123456' });
  ok(last.status === 429, 'email verrouille apres 10 echecs (IP variees, espaces et majuscules)');

  // --- Limites par IP : X-Forwarded-For falsifie a gauche sans effet ---
  let codesIp = [];
  for (let i = 0; i < 12; i++) {
    r = await post('/app/login/verify', { email: `ip${i}@b.fr`, code: '123456' }, `203.0.113.${i}, 198.51.100.7`);
    codesIp.push(r.status);
  }
  ok(codesIp.slice(10).every((s) => s === 429), 'limite par IP : 10/min, premiere entree X-Forwarded-For ignoree');
  codesIp = [];
  for (let i = 0; i < 12; i++) {
    r = await post('/app/login/verify', { email: `cf${i}@b.fr`, code: '123456' }, '198.51.100.8', { 'cf-connecting-ip': `203.0.113.${i}` });
    codesIp.push(r.status);
  }
  ok(codesIp.slice(10).every((s) => s === 429), 'cf-connecting-ip ignore sans secret d\'origine');

  // --- Journaux sans donnees personnelles ---
  mailerFails = true;
  await post('/app/login/request', { email: 'd@b.fr' }); await settle();
  mailerFails = false;
  ok(logs.some((l) => l.includes('EENVELOPE')) && logs.every((l) => !/@/.test(l)), `journaux sans adresse e-mail (${logs.length} ligne(s) : ${logs[logs.length - 1] || ''})`);
  ok(logs.every((l) => !/\b\d{6}\b/.test(l)), 'journaux sans code de connexion');

  // --- Flux d'evenements ---
  r = await fetch(base + '/app/events');
  ok(r.status === 401, 'events sans session : 401');
  r = await fetch(base + '/app/events', { headers: { Authorization: 'Bearer ' + 'f'.repeat(64) } });
  ok(r.status === 401 || (await r.json()).events, 'events : un jeton de lien magique n\'ouvre pas une session sans echange');
  r = await fetch(base + '/app/events?since_id=10&limit=1', { headers: { Authorization: 'Bearer SESSION' } }); j = await r.json();
  ok(r.status === 200 && j.events.length === 1 && j.has_more === true && j.last_id === 11, 'events pagination');
  ok(j.events[0].url === 'https://labonnealerte.fr/source/vigilance-meteo/statut?departement=05' && j.events[0].label === 'Hautes-Alpes', 'events url + libelle');
  const evQ = queries.filter((q) => q.sql.includes('FROM source_events')).pop();
  ok(evQ.params[0] === 7 && evQ.sql.includes('sub.subscriber_id = $1'), 'events : filtre par l\'abonne de la session (aucun autre compte)');
  ok(!/email/i.test(JSON.stringify(j)), 'events : aucune adresse dans la reponse');
  for (const [qs, since, lim] of [['since_id=abc&limit=xyz', 0, 101], ['since_id=-5&limit=0', 0, 2], ['since_id=1e99&limit=99999', 1, 201]]) {
    r = await fetch(base + '/app/events?' + qs, { headers: { Authorization: 'Bearer SESSION' } });
    const q = queries.filter((x) => x.sql.includes('FROM source_events')).pop();
    ok(r.status === 200 && q.params[1] === since && q.params[2] === lim, `events : parametres bornes (${qs} -> since ${q.params[1]}, limite ${q.params[2]})`);
  }

  // --- Forum en lecture ---
  r = await fetch(base + '/app/forum'); j = await r.json();
  ok(r.status === 200 && j.categories.find((c) => c.slug === 'entraide').count === 4, 'forum accueil');
  ok(queries.filter((q) => q.sql.includes('forum_topics')).every((q) => q.sql.includes('hidden = false') || q.sql.includes('WHERE ft.slug')), 'forum : toutes les listes filtrent hidden = false');
  r = await fetch(base + '/app/forum/topics?category=entraide&page=2'); j = await r.json();
  ok(r.status === 200 && j.topics.length === 30 && j.has_more === true && j.page === 2, 'forum liste paginee');
  r = await fetch(base + '/app/forum/topics?page=99999999');
  const pq = queries.filter((q) => q.sql.includes('OFFSET')).pop();
  ok(r.status === 200 && pq.params[pq.params.length - 1] === 999 * 30, 'forum : page bornee a 1000');
  r = await fetch(base + '/app/forum/topics?category=deck'); ok(r.status === 400, 'forum : categorie inconnue refusee');
  r = await fetch(base + '/app/forum/topics?source=' + encodeURIComponent("x' OR 1=1--"));
  const sq = queries.filter((q) => q.sql.includes('OFFSET')).pop();
  ok(r.status === 200 && !sq.sql.includes('source_id = $'), 'forum : source invalide ignoree (requetes parametrees)');
  r = await fetch(base + '/app/forum/topics/cache'); ok(r.status === 404, 'forum : sujet masque introuvable');
  r = await fetch(base + '/app/forum/topics/ok'); j = await r.json();
  ok(r.status === 200 && j.topic.id === 3 && j.posts[0].body === 'Bonjour @hugo' && !('deck_id' in j.topic), 'forum : sujet et messages, sans champ deck');
  ok(!JSON.stringify(j).includes('fuite@x.fr'), 'forum : liste blanche de champs (aucun email meme si la base en renvoie)');
  ok(queries.some((q) => q.sql.includes('fp.hidden = false')), 'forum : messages masques exclus');
  r = await fetch(base + '/app/forum/members/hugo'); j = await r.json();
  ok(r.status === 200 && Object.keys(j).sort().join() === 'display_name,pseudo', 'forum : fiche membre limitee au pseudo et au nom affiche');
  r = await fetch(base + '/app/forum/members/inconnu'); ok(r.status === 404, 'forum : membre inconnu 404');

  console.error = origError;
  console.log(fails ? `${fails} echec(s)` : 'Tous les tests passent');
  srv.close();
});
