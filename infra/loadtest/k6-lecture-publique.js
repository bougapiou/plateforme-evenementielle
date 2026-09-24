// Test de charge k6 — NAVIGATION PUBLIQUE (le plus gros volume : visiteurs qui consultent le site).
// Aucune écriture en base : ne crée rien, rien à nettoyer.
//
//   k6 run -e BASE_URL=https://<domaine> -e PROFILE=fumee k6-lecture-publique.js
//   PROFILE : fumee (30 s) | palier (~10 min, jusqu'à 300) | pic (500) ; SCALE : multiplicateur
//
// Parcours simulés : liste (avec recherche / filtres), détail + billets d'un événement, catégories,
// présence publique (écran de l'entrée), page d'accueil du site, image de couverture.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, PROFILE, alea, etapesUtilisateurs, exigerEnv, seuilsProtection } from './lib.js';

exigerEnv('BASE_URL');

export const options = {
  scenarios: {
    visiteurs: { executor: 'ramping-vus', startVUs: 0, stages: etapesUtilisateurs(), gracefulRampDown: '15s' },
  },
  thresholds: seuilsProtection({
    'http_req_duration{name:liste}': ['p(95)<1500'],
    'http_req_duration{name:detail}': ['p(95)<1500'],
    'http_req_duration{name:billets}': ['p(95)<1500'],
    'http_req_duration{name:presence}': ['p(95)<1500'],
  }),
};

export function setup() {
  const r = http.get(`${BASE}/api/public/events?size=12`);
  if (r.status !== 200) throw new Error(`Liste des événements inaccessible (${r.status})`);
  const evenements = (r.json('content') || []).map((e) => ({ slug: e.slug, cover: e.coverUrl || null }));
  if (!evenements.length) throw new Error('Aucun événement public : rien à naviguer.');
  console.log(`${evenements.length} événement(s) publics utilisés pour la navigation (${PROFILE}).`);
  return { evenements };
}

const RECHERCHES = ['', '', 'siao', 'fespaco', 'salon', 'forum', 'numérique'];
const VILLES = ['', '', '', 'Ouagadougou', 'Bobo-Dioulasso'];

export default function (data) {
  const tirage = Math.random();

  if (tirage < 0.45) {
    // Page d'accueil : la liste, filtrée ou non
    const q = alea(RECHERCHES);
    const v = alea(VILLES);
    const r = http.get(`${BASE}/api/public/events?size=12&search=${encodeURIComponent(q)}&ville=${encodeURIComponent(v)}`,
      { tags: { name: 'liste' } });
    check(r, { 'liste 200': (x) => x.status === 200 });
  } else if (tirage < 0.75) {
    // Détail d'un événement : fiche + billets (+ image de couverture, une fois sur trois)
    const e = alea(data.evenements);
    const fiche = http.get(`${BASE}/api/public/events/${e.slug}`, { tags: { name: 'detail' } });
    check(fiche, { 'détail 200': (x) => x.status === 200 });
    const billets = http.get(`${BASE}/api/public/events/${e.slug}/tickets`, { tags: { name: 'billets' } });
    check(billets, { 'billets 200': (x) => x.status === 200 });
    if (e.cover && Math.random() < 0.33) {
      http.get(e.cover, { tags: { name: 'couverture' } });
    }
  } else if (tirage < 0.85) {
    check(http.get(`${BASE}/api/event-categories`, { tags: { name: 'categories' } }),
      { 'catégories 200': (x) => x.status === 200 });
  } else if (tirage < 0.93) {
    // Écran « présence » public (rafraîchi toutes les quelques secondes à l'entrée)
    const e = alea(data.evenements);
    const r = http.get(`${BASE}/api/public/events/${e.slug}/attendance`, { tags: { name: 'presence' } });
    check(r, { 'présence 200': (x) => x.status === 200 });
  } else {
    // Le site web lui-même (fichiers statiques servis par nginx)
    check(http.get(`${BASE}/`, { tags: { name: 'site' } }), { 'site 200': (x) => x.status === 200 });
  }

  sleep(1 + Math.random() * 3); // temps de lecture d'un visiteur
}
