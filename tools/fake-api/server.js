// Serveur factice LaBonneAlerte pour le developpement de l'app.
// Sert les fixtures enregistrees depuis les GET publics de labonnealerte.fr.
// Ne contacte JAMAIS la production. Usage : node tools/fake-api/server.js [port]
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.argv[2] || process.env.PORT || 8787);
const FIX = path.join(__dirname, 'fixtures');

function load(name) {
  const body = fs.readFileSync(path.join(FIX, name));
  const etag = 'W/"' + crypto.createHash('sha1').update(body).digest('base64').slice(0, 27) + '"';
  return { body, etag };
}

const routes = {
  '/api/sources': { file: 'sources.json', maxAge: 60 },
  '/api/categories': { file: 'categories.json', maxAge: 3600 },
  '/api/le-point': { file: 'le-point.json', maxAge: 120 },
  '/api/geo': { file: 'geo.json', maxAge: 86400 },
  '/api/support-links': { file: 'support-links.json', maxAge: 3600 },
  '/api/stats': { file: 'stats.json', maxAge: 60 },
};

// Historique : fixture reelle si disponible, sinon 90 jours calmes generes.
function fakeHistory(id) {
  const p = path.join(FIX, `history-${id}.json`);
  if (fs.existsSync(p)) return fs.readFileSync(p);
  const days = [];
  for (let i = 89; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10);
    days.push({ date: d, status: i % 17 === 3 ? 'active' : 'calm' });
  }
  return JSON.stringify({ events: [], days });
}

// Extensions (ajoutees au fil des jalons) : routes dynamiques enregistrees ici.
const handlers = [];
function on(method, re, fn) { handlers.push({ method, re, fn }); }
module.exports = { on };

function send(res, status, obj, headers = {}) {
  const body = typeof obj === 'string' || Buffer.isBuffer(obj) ? obj : JSON.stringify(obj);
  // LBA_FAKE_NO_CHARSET=1 : Content-Type sans charset, pour verifier le decodage UTF-8 de l'app (APP-10).
  const type = process.env.LBA_FAKE_NO_CHARSET === '1' ? 'application/json' : 'application/json; charset=utf-8';
  res.writeHead(status, { 'Content-Type': type, ...headers });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  console.log(new Date().toISOString(), req.method, url.pathname);
  const r = routes[url.pathname];
  if ((req.method === 'GET' || req.method === 'HEAD') && r) {
    const { body, etag } = load(r.file);
    if (req.headers['if-none-match'] === etag) {
      res.writeHead(304, { ETag: etag });
      return res.end();
    }
    return send(res, 200, body, { ETag: etag, 'Cache-Control': `public, max-age=${r.maxAge}` });
  }
  let m;
  if (req.method === 'GET' && (m = url.pathname.match(/^\/api\/sources\/([^/]+)\/history$/))) {
    const dep = url.searchParams.get('departement');
    const key = dep ? `${m[1]}-${dep}` : m[1];
    return send(res, 200, fakeHistory(key));
  }
  if (req.method === 'GET' && url.pathname === '/api/communes') {
    const p = path.join(FIX, `communes-${url.searchParams.get('departement')}.json`);
    return fs.existsSync(p) ? send(res, 200, fs.readFileSync(p)) : send(res, 200, { communes: [] });
  }
  for (const h of handlers) {
    const m = req.method === h.method && url.pathname.match(h.re);
    if (m) return h.fn(req, res, m, url, send);
  }
  send(res, 404, { error: 'not_found' });
});

require('./app-routes')(on);

if (require.main === module) {
  server.listen(PORT, () => console.log(`fake-api sur http://localhost:${PORT} (emulateur : http://10.0.2.2:${PORT})`));
}
