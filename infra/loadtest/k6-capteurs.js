// Test de charge k6 — CAPTEURS LASER : plusieurs boîtiers (ESP32) envoient leurs passages
// (POST /api/sensors/entry | exit) une fois par seconde, comme le vrai firmware.
//
//   k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=<slug> \
//          -e ORGA_EMAIL=<organisateur> -e ORGA_PASSWORD=<mot de passe> \
//          -e SENSORS=20 -e PROFILE=fumee k6-capteurs.js
//
//   SENSORS : nombre de boîtiers simulés (défaut 10) ; PROFILE : fumee | palier | pic ; SCALE : multiplicateur
//   Les clés sont créées au départ sur l'événement de test (nommées « ZZ-CHARGE-n ») et RÉVOQUÉES à la fin.
//   Les passages comptés disparaissent avec l'événement de test au nettoyage (cleanup-2-suppression.sql).
//
// À la fin, comparez `passages_entree` / `passages_sortie` (récapitulatif k6) avec les totaux du serveur
// affichés dans le journal (« Totaux serveur »).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE, JSON_HEADERS, PROFILE, SCALE, connexion, evenementParSlug, exigerEnv, seuilsProtection,
} from './lib.js';

exigerEnv('BASE_URL', 'EVENT_SLUG', 'ORGA_EMAIL', 'ORGA_PASSWORD');

const NB = Math.max(1, Math.round(Number(__ENV.SENSORS || 10) * SCALE));
const entrees = new Counter('passages_entree');
const sorties = new Counter('passages_sortie');

const PROFILS = {
  fumee: [{ duration: '30s', target: Math.min(NB, 5) }],
  palier: [
    { duration: '1m', target: Math.ceil(NB / 4) }, { duration: '2m', target: Math.ceil(NB / 4) },
    { duration: '1m', target: Math.ceil(NB / 2) }, { duration: '2m', target: Math.ceil(NB / 2) },
    { duration: '1m', target: NB }, { duration: '3m', target: NB },
    { duration: '30s', target: 0 },
  ],
  pic: [{ duration: '15s', target: NB }, { duration: '2m', target: NB }, { duration: '15s', target: 0 }],
};

export const options = {
  scenarios: {
    boitiers: { executor: 'ramping-vus', startVUs: 0, stages: PROFILS[PROFILE], gracefulRampDown: '10s' },
  },
  thresholds: seuilsProtection({
    'http_req_duration{name:passage}': ['p(95)<800'],
  }),
};

function auth(token) {
  return { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
}

export function setup() {
  const evenement = evenementParSlug(__ENV.EVENT_SLUG);
  const token = connexion(__ENV.ORGA_EMAIL, __ENV.ORGA_PASSWORD);

  const capteurs = [];
  for (let i = 1; i <= NB; i++) {
    const r = http.post(`${BASE}/api/events/${evenement.id}/sensors`, JSON.stringify({ nom: `ZZ-CHARGE-${i}` }),
      { headers: auth(token), tags: { name: 'creation-capteur' } });
    if (r.status !== 201) throw new Error(`Création du capteur ${i} impossible (code ${r.status}) : ${r.body}`);
    capteurs.push({ id: r.json('capteur.id'), cle: r.json('cle') });
  }
  console.log(`${capteurs.length} capteur(s) créés sur « ${evenement.nom} ».`);
  return { eventId: evenement.id, token, capteurs };
}

export default function (data) {
  const capteur = data.capteurs[(__VU - 1) % data.capteurs.length];
  const sortie = Math.random() < 0.4; // 60 % d'entrées, 40 % de sorties
  const nombre = 1 + Math.floor(Math.random() * 3); // 1 à 3 passages regroupés
  const chemin = sortie ? 'exit' : 'entry';

  const r = http.post(`${BASE}/api/sensors/${chemin}?count=${nombre}`, null, {
    headers: { 'X-Sensor-Key': capteur.cle }, tags: { name: 'passage' },
  });
  if (check(r, { 'passage 200': (x) => x.status === 200 })) {
    (sortie ? sorties : entrees).add(nombre);
  }
  sleep(1); // le firmware envoie une fois par seconde
}

export function teardown(data) {
  // Totaux vus par le serveur, à comparer avec passages_entree / passages_sortie
  const a = http.get(`${BASE}/api/events/${data.eventId}/attendance`, { headers: auth(data.token) });
  if (a.status === 200) {
    const p = a.json('comptagePhysique');
    console.log(`Totaux serveur (tout l'événement) : ${p.entrees} entrées, ${p.sorties} sorties, ${p.presents} présents.`);
  }
  for (const c of data.capteurs) {
    http.del(`${BASE}/api/events/${data.eventId}/sensors/${c.id}`, null, { headers: auth(data.token) });
  }
  console.log(`${data.capteurs.length} clé(s) de capteur révoquée(s).`);
}
