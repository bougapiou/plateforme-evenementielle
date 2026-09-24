// Test de charge k6 — parcours « obtenir un billet gratuit » (le plus lourd) + navigation.
//
// Usage (voir README.md pour la procédure complète) :
//   k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=zz-test-charge -e PROFILE=fumee k6-billet-gratuit.js
//
// PROFILE : fumee (5 utilisateurs, 30 s) | palier (montée par paliers) | pic (pic brutal)
// SCALE   : multiplicateur du nombre d'utilisateurs virtuels (défaut 1)
//
// Sécurité : le test S'ARRÊTE TOUT SEUL si plus de 5 % des requêtes échouent
// ou si le temps de réponse dépasse 3 s (95e centile) — pour ne pas abîmer le serveur.
//
// Toutes les données créées sont repérables : numéros « +226 99999… » (invités) et
// l'événement de test dédié. Le script SQL cleanup-2-suppression.sql les supprime.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = (__ENV.BASE_URL || '').replace(/\/$/, '');
const SLUG = __ENV.EVENT_SLUG;
const PROFILE = __ENV.PROFILE || 'fumee';
const SCALE = Number(__ENV.SCALE || 1);
const FULL_FLOW_RATIO = Number(__ENV.FULL_FLOW_RATIO || 0.3); // 30 % prennent un billet, 70 % naviguent

if (!BASE || !SLUG) {
  throw new Error('Renseignez -e BASE_URL=https://... et -e EVENT_SLUG=<slug de l\'événement de test>');
}

const s = (n) => Math.max(1, Math.round(n * SCALE));
const PROFILES = {
  fumee: [{ duration: '30s', target: 5 }],
  palier: [
    { duration: '2m', target: s(20) },
    { duration: '3m', target: s(20) },
    { duration: '2m', target: s(50) },
    { duration: '3m', target: s(50) },
    { duration: '2m', target: s(100) },
    { duration: '3m', target: s(100) },
    { duration: '1m', target: 0 },
  ],
  pic: [
    { duration: '20s', target: s(200) },
    { duration: '2m', target: s(200) },
    { duration: '30s', target: 0 },
  ],
};

export const options = {
  scenarios: {
    visiteurs: { executor: 'ramping-vus', startVUs: 0, stages: PROFILES[PROFILE], gracefulRampDown: '15s' },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '20s' }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:guest-quick}': ['p(95)<2000'],
    'http_req_duration{name:inscription}': ['p(95)<2500'],
    'http_req_duration{name:pdf}': ['p(95)<3000'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function setup() {
  const ev = http.get(`${BASE}/api/public/events/${SLUG}`);
  if (ev.status !== 200) throw new Error(`Événement de test introuvable (${ev.status}) — slug : ${SLUG}`);
  const eventId = ev.json('id');

  const tk = http.get(`${BASE}/api/public/events/${SLUG}/tickets`);
  const free = (tk.json() || []).find((t) => t.prixMontant === 0 && t.enVente);
  if (!free) throw new Error('Aucune catégorie de billet GRATUITE en vente sur cet événement de test.');
  if (free.formulaireRequis) console.warn('La catégorie exige un formulaire : le test envoie un nom « Visiteur ».');

  return { eventId, ticketId: free.id, run: Math.floor(Math.random() * 1000) };
}

const pad = (n, len) => String(n).padStart(len, '0');

export default function (data) {
  // 1) Navigation (tous les visiteurs)
  const list = http.get(`${BASE}/api/public/events?size=12`, { tags: { name: 'liste' } });
  check(list, { 'liste 200': (r) => r.status === 200 });
  sleep(0.5 + Math.random());
  const detail = http.get(`${BASE}/api/public/events/${SLUG}`, { tags: { name: 'detail' } });
  check(detail, { 'détail 200': (r) => r.status === 200 });

  if (Math.random() > FULL_FLOW_RATIO) {
    sleep(1 + Math.random() * 2);
    return;
  }

  // 2) Prendre un billet gratuit, comme un invité (numéro « +226 99999… » = marqueur de test)
  const phone = `+226 99999${pad(data.run, 3)}${pad(__VU, 3)}${pad(__ITER, 4)}`;
  const guest = http.post(`${BASE}/api/auth/guest-quick`, JSON.stringify({ phone }), {
    headers: JSON_HEADERS, tags: { name: 'guest-quick' },
  });
  if (!check(guest, { 'session invité 200': (r) => r.status === 200 })) return;
  const auth = { ...JSON_HEADERS, Authorization: `Bearer ${guest.json('accessToken')}` };

  const reg = http.post(
    `${BASE}/api/events/${data.eventId}/registrations`,
    JSON.stringify({
      type: 'PARTICULIER',
      participants: [{ nom: 'Visiteur' }],
      tickets: [{ eventTicketId: data.ticketId, quantite: 1 }],
    }),
    { headers: auth, tags: { name: 'inscription' } },
  );
  if (!check(reg, { 'inscription 2xx': (r) => r.status >= 200 && r.status < 300 })) return;

  // 3) Récupérer et télécharger le billet (génération QR + PDF côté serveur)
  const mine = http.get(`${BASE}/api/tickets/my`, { headers: auth, tags: { name: 'mes-billets' } });
  const tickets = mine.status === 200 ? mine.json() : [];
  if (tickets.length) {
    const pdf = http.get(`${BASE}/api/tickets/${tickets[0].id}/pdf`, {
      headers: auth, tags: { name: 'pdf' }, responseType: 'binary',
    });
    check(pdf, {
      'pdf 200': (r) => r.status === 200,
      'pdf non vide': (r) => r.body && r.body.byteLength > 1000,
    });
  }
  sleep(1 + Math.random() * 2);
}
