// Enregistre les fixtures depuis les GET PUBLICS de la production (lecture seule).
// Usage : node tools/fake-api/record.js
'use strict';
const fs = require('fs');
const path = require('path');
const BASE = 'https://labonnealerte.fr';
const PATHS = {
  'sources.json': '/api/sources',
  'categories.json': '/api/categories',
  'le-point.json': '/api/le-point',
  'geo.json': '/api/geo',
  'communes-05.json': '/api/communes?departement=05',
  'history-vigilance-meteo-05.json': '/api/sources/vigilance-meteo/history?departement=05',
  'alert-vigilance-meteo-05.json': '/api/sources/vigilance-meteo/alert.json?departement=05',
  'history-leboncoin-livraison.json': '/api/sources/leboncoin-livraison/history',
  'support-links.json': '/api/support-links',
  'stats.json': '/api/stats',
};
(async () => {
  for (const [file, p] of Object.entries(PATHS)) {
    const r = await fetch(BASE + p, { headers: { 'User-Agent': 'LBA-app-dev-fixtures' } });
    if (!r.ok) { console.log(file, 'HTTP', r.status); continue; }
    fs.writeFileSync(path.join(__dirname, 'fixtures', file), await r.text());
    console.log(file, 'OK');
  }
})();
