// Relais ntfy minimal pour tester UnifiedPush sur emulateur, sans Docker ni service externe.
// Il implemente le strict necessaire du protocole ntfy utilise par l'app ntfy Android :
//   GET  /<topics>/json[?poll=1&since=…]  flux JSON (une ligne par evenement)
//   POST|PUT /<topic>[?up=1]              publication (Web Push chiffre : corps binaire -> base64)
// Usage : node tools/fake-ntfy/server.js   (port 8090), puis adb reverse tcp:8090 tcp:8090
// et, dans ntfy : Parametres > Serveur par defaut = http://127.0.0.1:8090
'use strict';

const http = require('http');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT || '8090', 10);
const listeners = new Map(); // topic -> Set<res>
const stored = new Map();    // topic -> [message]

function log(...a) { console.log(new Date().toISOString().slice(11, 19), ...a); }

function sendLine(res, obj) { res.write(JSON.stringify(obj) + '\n'); }

function publish(topic, body, headers) {
  const utf8 = Buffer.from(body.toString('utf8'), 'utf8').equals(body);
  const binary = headers['content-encoding'] === 'aes128gcm' || !utf8;
  const msg = {
    id: crypto.randomBytes(6).toString('hex'),
    time: Math.floor(Date.now() / 1000),
    event: 'message',
    topic,
    message: binary ? body.toString('base64') : body.toString('utf8'),
  };
  if (binary) msg.encoding = 'base64';
  if (!stored.has(topic)) stored.set(topic, []);
  stored.get(topic).push(msg);
  const subs = listeners.get(topic) || new Set();
  for (const res of subs) sendLine(res, msg);
  log(`publication ${topic} : ${body.length} octets${binary ? ' (binaire)' : ''}, ${subs.size} abonne(s)`);
  return msg;
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  const parts = url.pathname.split('/').filter(Boolean);
  log(req.method, url.pathname + (url.search ? ' ' + url.search : ''));

  if (url.pathname === '/v1/health') return res.end('{"healthy":true}');
  if (url.pathname === '/v1/config' || url.pathname === '/config.js') {
    res.setHeader('Content-Type', 'application/json');
    return res.end('{}');
  }

  if (req.method === 'GET' && parts.length === 2 && parts[1] === 'json') {
    const topics = parts[0].split(',');
    res.writeHead(200, { 'Content-Type': 'application/x-ndjson; charset=utf-8' });
    const since = url.searchParams.get('since');
    const all = topics.flatMap((t) => stored.get(t) || []);
    let backlog = [];
    if (since === 'all') backlog = all;
    else if (since && /^\d+$/.test(since)) backlog = all.filter((m) => m.time >= Number(since));
    else if (since && since !== 'none') backlog = all.slice(all.findIndex((m) => m.id === since) + 1);
    if (url.searchParams.get('poll') === '1') {
      backlog.forEach((m) => sendLine(res, m));
      return res.end();
    }
    sendLine(res, { id: crypto.randomBytes(6).toString('hex'), time: Math.floor(Date.now() / 1000), event: 'open', topic: parts[0] });
    backlog.forEach((m) => sendLine(res, m));
    topics.forEach((t) => { if (!listeners.has(t)) listeners.set(t, new Set()); listeners.get(t).add(res); });
    const keep = setInterval(() => sendLine(res, { id: crypto.randomBytes(6).toString('hex'), time: Math.floor(Date.now() / 1000), event: 'keepalive', topic: parts[0] }), 25_000);
    req.on('close', () => { clearInterval(keep); topics.forEach((t) => listeners.get(t)?.delete(res)); log('abonne deconnecte', parts[0]); });
    log('abonne connecte', parts[0]);
    return undefined;
  }

  if ((req.method === 'POST' || req.method === 'PUT') && parts.length === 1) {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const msg = publish(parts[0], Buffer.concat(chunks), req.headers);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(msg));
    });
    return undefined;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  return res.end('{"code":40401,"http":404,"error":"page not found"}');
});

server.listen(PORT, () => log(`relais ntfy factice sur http://127.0.0.1:${PORT}`));
