// Test de charge k6 — CONTRÔLE À L'ENTRÉE : des agents scannent des billets QR (POST /api/checkins/scan),
// avec un débit qui monte comme à l'ouverture des portes.
//
// Étapes (voir README.md) :
//   1. k6-billet-gratuit.js  -> crée des billets sur l'événement de test ZZ-TEST-CHARGE…
//   2. export-tokens.sql     -> exporte leurs jetons QR dans tokens.csv (à placer à côté de ce script)
//   3. ce script             -> les scanne
//
//   k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=<slug> \
//          -e SCANNER_EMAIL=<organisateur> -e SCANNER_PASSWORD=<mot de passe> -e PROFILE=fumee k6-scan-entree.js
//   PROFILE : fumee (3 scans/s) | palier (10 -> 30 -> 60 scans/s) | pic (100 scans/s) ; SCALE : multiplicateur
//
// Mélange : 80 % de premiers scans (attendu : VALIDE), 15 % de re-scans (attendu : DEJA_UTILISE),
// 5 % de faux QR (attendu : INVALIDE). Hypothèse : le « contrôle des sorties » n'est PAS activé sur
// l'événement de test (sinon un 2e scan d'entrée est une ré-entrée).
//
// Le compte SCANNER doit pouvoir contrôler l'événement (l'organisateur de l'événement de test convient).

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import {
  BASE, JSON_HEADERS, alea, connexion, etapesDebit, evenementParSlug, exigerEnv, iterationsPrevues,
  seuilsProtection,
} from './lib.js';

exigerEnv('BASE_URL', 'EVENT_SLUG', 'SCANNER_EMAIL', 'SCANNER_PASSWORD');

const jetons = new SharedArray('jetons', () =>
  open(__ENV.TOKENS_FILE || 'tokens.csv').split('\n').map((l) => l.trim().replace(/"/g, '')).filter(Boolean));

const valide = new Counter('scan_valide');
const dejaUtilise = new Counter('scan_deja_utilise');
const invalide = new Counter('scan_invalide');
const inattendu = new Rate('scan_resultat_inattendu');

const etapes = etapesDebit();

export const options = {
  scenarios: {
    porte: {
      executor: 'ramping-arrival-rate', startRate: 1, timeUnit: '1s',
      preAllocatedVUs: 50, maxVUs: 300, stages: etapes,
    },
  },
  thresholds: seuilsProtection({
    'http_req_duration{name:scan}': ['p(95)<1000'],
    scan_resultat_inattendu: ['rate<0.02'],
  }),
};

export function setup() {
  if (jetons.length === 0) throw new Error('tokens.csv est vide : lancez d\'abord export-tokens.sql.');
  const evenement = evenementParSlug(__ENV.EVENT_SLUG);
  const attendus = iterationsPrevues(etapes);
  if (jetons.length < attendus * 0.8) {
    console.warn(`Seulement ${jetons.length} jetons pour ~${attendus} scans prévus (dont 80 % de premiers scans) : `
      + 'les derniers seront « déjà utilisés ». Générez plus de billets (k6-billet-gratuit.js) ou baissez SCALE.');
  }
  return { eventId: evenement.id, token: connexion(__ENV.SCANNER_EMAIL, __ENV.SCANNER_PASSWORD) };
}

// Chaque VU garde son propre jeton de session (renouvelé s'il expire : durée de vie 15 min).
let sessionToken = null;
function entetes(data) {
  return { ...JSON_HEADERS, Authorization: `Bearer ${sessionToken || data.token}` };
}

function scanner(data, jeton) {
  const corps = JSON.stringify({ token: jeton, eventId: data.eventId, sens: 'ENTREE' });
  let r = http.post(`${BASE}/api/checkins/scan`, corps, { headers: entetes(data), tags: { name: 'scan' } });
  if (r.status === 401) { // jeton de session expiré : on se reconnecte une fois
    sessionToken = connexion(__ENV.SCANNER_EMAIL, __ENV.SCANNER_PASSWORD);
    r = http.post(`${BASE}/api/checkins/scan`, corps, { headers: entetes(data), tags: { name: 'scan' } });
  }
  return r;
}

export default function (data) {
  const n = exec.scenario.iterationInTest;
  const tirage = Math.random();

  let attendu;
  let jeton;
  if (tirage < 0.05) {
    jeton = `FAUX-${Math.random().toString(36).slice(2)}`;
    attendu = 'INVALIDE';
  } else if (tirage < 0.20 && n > 0) {
    jeton = jetons[Math.floor(Math.random() * Math.min(n, jetons.length))]; // déjà scanné plus tôt
    attendu = 'DEJA_UTILISE';
  } else {
    jeton = jetons[n % jetons.length];
    attendu = n < jetons.length ? 'VALIDE' : 'DEJA_UTILISE';
  }

  const r = scanner(data, jeton);
  const ok = check(r, { 'scan 200': (x) => x.status === 200 });
  if (!ok) { inattendu.add(true); return; }

  const resultat = r.json('resultat');
  if (resultat === 'VALIDE') valide.add(1);
  else if (resultat === 'DEJA_UTILISE') dejaUtilise.add(1);
  else invalide.add(1);
  inattendu.add(resultat !== attendu);
}
