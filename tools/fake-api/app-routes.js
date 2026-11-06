// Routes factices de l'app (server/routes/app-api.js + compte minimal) pour le developpement.
// Tout est en memoire. Jeton de session factice : « fake-session ». Code de connexion : 123456.
'use strict';

const events = [];
let nextId = 1000;
const pushSubs = new Map();

// Web Push reel (bibliotheque web-push du site) avec des cles VAPID jetables, generees au
// demarrage : /dev/emit chiffre et envoie au point d'acces UnifiedPush enregistre par l'app
// (ex. relais tools/fake-ntfy). Sans la bibliotheque, l'envoi est simplement ignore.
let webpush = null;
let VAPID = { publicKey: 'BFAKEFAKE' };
try {
  webpush = require(require.resolve('web-push', { paths: [process.env.LBA_SITE_DIR || 'C:/Dev/Labonnealerte'] }));
  VAPID = webpush.generateVAPIDKeys();
  webpush.setVapidDetails('mailto:test@example.invalid', VAPID.publicKey, VAPID.privateKey);
} catch (e) {
  console.warn('[fake-api] web-push introuvable : push desactive, releve seule');
}

// web-push n'envoie qu'en https : pour le relais local (http://127.0.0.1, tools/fake-ntfy) on chiffre
// avec generateRequestDetails puis on envoie nous-memes la requete en http.
function sendWebPush(sub, payload) {
  if (!sub.endpoint.startsWith('http://127.0.0.1:')) return webpush.sendNotification(sub, payload).then((r) => r.statusCode);
  const d = webpush.generateRequestDetails(sub, payload);
  return new Promise((resolve, reject) => {
    const req = require('http').request(d.endpoint, { method: d.method, headers: d.headers }, (res) => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject);
    req.end(d.body);
  });
}
let subscribed = { 'vigilance-meteo': [{ departement: '05' }] };
// Pseudo fictif (pastille a initiale, APP-12) ; modifiable par /api/my-alerts/display-name.
let displayName = 'camille';
const tasks = [{ id: 1, label: 'Contrôle technique', tracking_mode: 'time', next_due: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10) }];
let nextTask = 2;

function readBody(req) {
  return new Promise((resolve) => {
    let s = '';
    req.on('data', (d) => (s += d));
    req.on('end', () => {
      try { resolve(s ? JSON.parse(s) : {}); } catch (e) { resolve({}); }
    });
  });
}

const TERMS_VERSION = 'confidentialite-2026-10';

function authed(req) {
  return (req.headers.authorization || '') === 'Bearer fake-session';
}

module.exports = function install(on) {
  on('GET', /^\/api\/app\/config$/, (req, res, m, url, send) => send(res, 200, {
    min_version_code: 0, latest_version_code: 0, maintenance: false, message: null, vapid_public_key: VAPID.publicKey,
    terms_version: TERMS_VERSION, terms_url: 'https://labonnealerte.fr/confidentialite',
  }));

  on('POST', /^\/api\/app\/login\/request$/, (req, res, m, url, send) =>
    send(res, 200, { message: 'Un code à 6 chiffres vient d’être envoyé à cette adresse.' }));

  on('POST', /^\/api\/app\/login\/verify$/, async (req, res, m, url, send) => {
    const b = await readBody(req);
    const email = String(b.email || 'test@exemple.fr').trim().toLowerCase();
    // Creation de compte (APP-04) : toute adresse commencant par « nouveau » est inconnue.
    if (b.code === '123456' && email.startsWith('nouveau')) {
      if (b.accept_terms !== true || b.terms_version !== TERMS_VERSION) {
        return send(res, 409, { needs_terms: true, terms_version: TERMS_VERSION, terms_url: 'https://labonnealerte.fr/confidentialite' });
      }
      return send(res, 200, { token: 'fake-session', email, created: true });
    }
    if (b.code === '123456') return send(res, 200, { token: 'fake-session', email });
    return send(res, 400, { error: 'Code incorrect ou expiré' });
  });

  on('GET', /^\/api\/my-alerts$/, (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide' });
    send(res, 200, {
      email: 'test@exemple.fr', token: 'fake-session', display_name: displayName, favorites: Object.keys(subscribed),
      sources: [{ id: 'mes-taches', name: 'Mes échéances', subscribed: true, muted: false, state: 'inactive', instances: [], tasks }].concat(Object.entries(subscribed).map(([id, inst]) => ({
        id, name: id, subscribed: true, muted: false, state: 'inactive',
        instances: inst.map((p) => ({ params: p, label: Object.values(p).join(', '), state: 'inactive', muted: false })),
      }))),
      sources_tasks: undefined,
      country: 'FR', departement: '05', region: "Provence-Alpes-Côte d'Azur", ville: 'Gap', interests: [],
      quiet_start: 23, quiet_end: 8, quiet_disabled: false, email_enabled: true, push_endpoints_count: pushSubs.size,
    });
  });

  on('GET', /^\/api\/my-alerts\/history$/, (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide' });
    send(res, 200, { events: events.slice(-20).reverse().map((e) => ({ ...e, id: undefined })) });
  });

  on('POST', /^\/api\/my-alerts\/(toggle|toggle-param|toggle-mute|profile|display-name|quiet-hours)$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide' });
    const b = await readBody(req);
    if (m[1] === 'toggle-param' && b.source_id) {
      const list = subscribed[b.source_id] || [];
      subscribed[b.source_id] = b.subscribed ? [...list, b.params] : list.filter((p) => JSON.stringify(p) !== JSON.stringify(b.params));
      if (!subscribed[b.source_id].length) delete subscribed[b.source_id];
      return send(res, 200, { source_id: b.source_id, subscribed: !!b.subscribed, params: b.params, label: Object.values(b.params || {}).join(', ') });
    }
    if (m[1] === 'display-name' && typeof b.display_name === 'string') {
      displayName = b.display_name.trim().slice(0, 40);
      return send(res, 200, { display_name: displayName });
    }
    if (m[1] === 'toggle' && b.source_id) {
      if (b.subscribed) subscribed[b.source_id] = []; else delete subscribed[b.source_id];
    }
    send(res, 200, { ...b, token: undefined });
  });

  on('POST', /^\/api\/logout$/, (req, res, m, url, send) => send(res, 200, { ok: true }));

  on('GET', /^\/api\/push\/vapid-key$/, (req, res, m, url, send) => send(res, 200, { key: VAPID.publicKey, enabled: !!webpush }));
  on('POST', /^\/api\/push\/(subscribe|unsubscribe)$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide' });
    const b = await readBody(req);
    if (m[1] === 'subscribe' && b.subscription) pushSubs.set(b.subscription.endpoint, b.subscription.keys);
    if (m[1] === 'unsubscribe') pushSubs.delete(b.endpoint);
    send(res, 200, { ok: true });
  });

  on('GET', /^\/api\/app\/events$/, (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide ou expirée' });
    const since = parseInt(url.searchParams.get('since_id') || '0', 10) || 0;
    const limit = Math.min(Math.max(parseInt(url.searchParams.get('limit') || '100', 10) || 100, 1), 200);
    const after = events.filter((e) => e.id > since);
    const page = after.slice(0, limit);
    send(res, 200, { events: page, last_id: page.length ? page[page.length - 1].id : since, has_more: after.length > limit });
  });

  // Forum (lecture JSON de server/routes/app-api.js + ecritures des routes existantes).
  const topics = [
    { id: 1, slug: 'bienvenue', title: 'Bienvenue sur le forum', category: 'discussion', reply_count: 1, last_reply_at: new Date().toISOString(), locked: false, source_id: null, source_name: null, author: 'Hugo', author_pseudo: 'hugo' },
    { id: 2, slug: 'vigilance-gap', title: 'Orages sur Gap ce soir ?', category: 'entraide', reply_count: 0, last_reply_at: new Date().toISOString(), locked: false, source_id: 'vigilance-meteo', source_name: 'Vigilance météo', author: 'Membre', author_pseudo: null },
  ];
  const posts = { 1: [{ id: 10, body: 'Bonjour à tous, présentez-vous ici.', created_at: new Date().toISOString(), author: 'Hugo', author_pseudo: 'hugo' }], 2: [] };
  const cats = { discussion: 'Discussion libre', propositions: 'Propositions', dev: 'Espace dev', entraide: 'Entraide', suggestions: 'Suggestions' };

  on('GET', /^\/api\/app\/forum$/, (req, res, m, url, send) => send(res, 200, {
    categories: Object.entries(cats).map(([slug, label]) => ({ slug, label, count: topics.filter((t) => t.category === slug).length })),
    recent: topics,
  }));
  on('GET', /^\/api\/app\/forum\/topics$/, (req, res, m, url, send) => {
    const c = url.searchParams.get('category'); const s = url.searchParams.get('source'); const a = url.searchParams.get('author');
    send(res, 200, { topics: topics.filter((t) => (!c || t.category === c) && (!s || t.source_id === s) && (!a || t.author_pseudo === a)), page: 1, has_more: false });
  });
  on('GET', /^\/api\/app\/forum\/topics\/([^/]+)$/, (req, res, m, url, send) => {
    const t = topics.find((x) => x.slug === m[1]);
    if (!t) return send(res, 404, { error: 'Sujet introuvable' });
    send(res, 200, { topic: t, posts: posts[t.id] || [], page: 1, has_more: false });
  });
  on('POST', /^\/forum\/t$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Connexion requise' });
    const b = await readBody(req);
    const id = topics.length + 1; const slug = 'sujet-' + id;
    topics.unshift({ id, slug, title: b.title, category: b.category, reply_count: 0, last_reply_at: new Date().toISOString(), locked: false, source_id: b.source_id || null, source_name: null, author: 'Test', author_pseudo: 'test' });
    posts[id] = [{ id: 100 + id, body: b.body, created_at: new Date().toISOString(), author: 'Test', author_pseudo: 'test' }];
    send(res, 201, { slug, url: '/forum/t/' + slug });
  });
  on('POST', /^\/forum\/t\/(\d+)\/reply$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Connexion requise' });
    const b = await readBody(req);
    (posts[m[1]] = posts[m[1]] || []).push({ id: Date.now(), body: b.body, created_at: new Date().toISOString(), author: 'Test', author_pseudo: 'test' });
    send(res, 201, { ok: true });
  });
  on('POST', /^\/forum\/post\/(\d+)\/report$/, (req, res, m, url, send) => send(res, authed(req) ? 200 : 401, { ok: authed(req), hidden: false }));

  // Signalements communautaires (server/routes/community-reports.js).
  const reports = [{ id: 1, type: 'chat-perdu', commune_nom: 'Gap', commune_insee: '05061', lat: 44.56, lon: 6.08, radius_km: 15, description: 'Chat tigré, collier rouge, répond à « Minou ».', link: null, created_at: new Date().toISOString(), expires_at: new Date(Date.now() + 6 * 86400000).toISOString(), extensions_count: 0, status: 'active', spot_count: 1, subscribed: false, spotted: false, is_author: false }];
  on('GET', /^\/api\/community-reports$/, (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide ou expirée' });
    const t = url.searchParams.get('type') || 'chat-perdu';
    send(res, 200, reports.filter((r) => r.type === t));
  });
  on('POST', /^\/api\/community-reports$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide ou expirée' });
    const b = await readBody(req);
    if (!b.ville) return send(res, 400, { error: 'commune requise' });
    const r = { ...reports[0], id: reports.length + 1, type: b.type, commune_nom: b.ville, radius_km: b.radius_km, description: b.description, link: b.link, spot_count: 0, is_author: true, subscribed: true };
    reports.push(r);
    send(res, 201, { created: true, report: r });
  });
  on('POST', /^\/api\/community-reports\/(\d+)\/(spot|extend|resolve)$/, (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide ou expirée' });
    const r = reports.find((x) => x.id === Number(m[1]));
    if (!r) return send(res, 404, { error: 'introuvable' });
    if (m[2] === 'spot') { r.spot_count++; r.spotted = true; }
    if (m[2] === 'extend') r.extensions_count++;
    if (m[2] === 'resolve') r.status = 'resolved';
    send(res, 200, { ok: true, spot_count: r.spot_count, status: r.status });
  });

  on('POST', /^\/api\/user-tasks$/, async (req, res, m, url, send) => {
    if (!authed(req)) return send(res, 401, { error: 'Session invalide ou expirée' });
    const b = await readBody(req);
    if (!b.label || !b.anchor_date) return send(res, 400, { error: 'Champs manquants' });
    const t = { id: nextTask++, label: b.label, tracking_mode: 'time', next_due: b.anchor_date };
    tasks.push(t);
    send(res, 201, { task: t });
  });
  on('POST', /^\/api\/user-tasks\/(\d+)\/confirm$/, (req, res, m, url, send) => {
    const t = tasks.find((x) => x.id === Number(m[1]));
    if (!t) return send(res, 404, { error: 'Tâche inconnue' });
    t.next_due = new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10);
    send(res, 200, { id: t.id, next_due: t.next_due, message: 'Échéance décalée' });
  });
  on('DELETE', /^\/api\/user-tasks\/(\d+)$/, (req, res, m, url, send) => {
    const i = tasks.findIndex((x) => x.id === Number(m[1]));
    if (i >= 0) tasks.splice(i, 1);
    send(res, 200, { id: Number(m[1]), deleted: true, subscribed: false });
  });

  // Outil de developpement : emettre un evenement (curl -X POST .../dev/emit -d '{"source_id":"vigilance-meteo","message":"..."}')
  on('POST', /^\/dev\/emit$/, async (req, res, m, url, send) => {
    const b = await readBody(req);
    const e = {
      id: nextId++,
      source_id: b.source_id || 'vigilance-meteo',
      source_name: b.source_name || 'Vigilance météo',
      event: b.event || 'activated',
      message: b.message || "⛈️ Demain : vigilance ORANGE dans Hautes-Alpes : orages",
      label: b.label || 'Hautes-Alpes',
      categories: b.categories || ['vigilance-meteo'],
      created_at: new Date().toISOString(),
      url: `https://labonnealerte.fr/source/${b.source_id || 'vigilance-meteo'}/statut`,
    };
    events.push(e);
    // Meme charge utile que server/webpush.js du site : { title, body, url }.
    const payload = JSON.stringify({ title: e.source_name, body: e.message || 'Retour au calme', url: e.url });
    const results = [];
    if (webpush) {
      for (const [endpoint, keys] of pushSubs) {
        try {
          const status = await sendWebPush({ endpoint, keys }, payload);
          results.push({ endpoint, status });
        } catch (err) {
          results.push({ endpoint, status: err.statusCode || String(err.message).slice(0, 80) });
        }
      }
    }
    send(res, 200, { ...e, push: results });
  });
};
