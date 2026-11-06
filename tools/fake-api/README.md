# fake-api

Serveur factice pour developper l'app sans jamais toucher la production.

    node tools/fake-api/server.js        # port 8787

Depuis l'emulateur : `http://10.0.2.2:8787`. Les fixtures de `fixtures/` sont des
copies des GET publics (`/api/sources`, `/api/categories`) enregistrees le 2026-10-08.
Pour les rafraichir : `node tools/fake-api/record.js` (GET publics uniquement).
