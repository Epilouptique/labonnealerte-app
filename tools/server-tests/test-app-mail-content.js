// Test hors ligne du CONTENU des e-mails de code de l'application (server/mailer.js, APP-05) :
// code seul, aucun lien (les App Links ne sont pas publies, un lien https ouvrirait le navigateur).
// Resend et la base sont simules : aucun e-mail envoye, aucune base.
// Usage : node tools/server-tests/test-app-mail-content.js
'use strict';
const path = require('path');
const SRV = 'C:/Dev/Labonnealerte/server';

const sent = [];
const resendFile = require.resolve('resend', { paths: [SRV] });
require.cache[resendFile] = {
  id: resendFile, filename: resendFile, loaded: true,
  exports: { Resend: function Resend() { this.emails = { send: async (m) => { sent.push(m); return { id: 'test' }; } }; } },
};
const dbFile = require.resolve(path.join(SRV, 'db'));
require.cache[dbFile] = { id: dbFile, filename: dbFile, loaded: true, exports: { pool: { query: async () => ({ rows: [] }) } } };

const mailer = require(path.join(SRV, 'mailer'));
let fails = 0;
const ok = (c, msg) => { console.log((c ? 'OK   ' : 'ECHEC') + ' ' + msg); if (!c) { fails++; process.exitCode = 1; } };

(async () => {
  const token = 'a'.repeat(64);
  await mailer.sendAppLoginCode('connu@test.fr', token, '123456');
  await mailer.sendAppSignupCode('nouveau@test.fr', '654321');
  const [login, signup] = sent;

  for (const [nom, m, code] of [['connexion', login, '123456'], ['creation', signup, '654321']]) {
    ok(m.text.includes(code) && m.html.includes(code), `${nom} : le code figure dans le texte et le HTML`);
    ok(!/https?:\/\//.test(m.text), `${nom} : aucun lien dans la version texte`);
    // Ni bouton, ni lien de repli, ni lien de pied de page vers le site.
    const hrefs = [...m.html.matchAll(/href="([^"]+)"/g)].map((x) => x[1]);
    ok(hrefs.length === 0, `${nom} : aucun lien dans le HTML (${hrefs.join(', ') || 'aucun'})`);
    ok(!m.html.includes(token) && !m.text.includes(token), `${nom} : le jeton n'apparait nulle part`);
  }
  // Les e-mails du site gardent leur pied de page avec le lien vers le site.
  await mailer.sendMagicLink('site@test.fr', token);
  ok(sent[2].html.includes('href="https://labonnealerte.fr/connexion"'), 'site (lien magique) : pied de page inchange');
  ok(login.subject.includes('Ton code de connexion'), 'connexion : objet inchange');
  ok(signup.subject.includes('Ton code pour créer ton compte'), 'creation : objet inchange');

  console.log(fails ? `\n${fails} echec(s)` : '\nTous les cas passent.');
})();
