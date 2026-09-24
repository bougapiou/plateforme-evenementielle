// Fonctions communes aux scripts k6 de ce dossier.
import http from 'k6/http';

export const BASE = (__ENV.BASE_URL || '').replace(/\/$/, '');
export const PROFILE = __ENV.PROFILE || 'fumee';
export const SCALE = Number(__ENV.SCALE || 1);
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function exigerEnv(...noms) {
  const manquants = noms.filter((n) => !__ENV[n]);
  if (manquants.length) {
    throw new Error('Variables manquantes : ' + manquants.map((n) => `-e ${n}=...`).join(' '));
  }
}

const s = (n) => Math.max(1, Math.round(n * SCALE));

/** Profils de montée en charge en NOMBRE D'UTILISATEURS VIRTUELS (ramping-vus). */
export function etapesUtilisateurs(profil = PROFILE) {
  const p = {
    fumee: [{ duration: '30s', target: 5 }],
    palier: [
      { duration: '1m', target: s(50) }, { duration: '2m', target: s(50) },
      { duration: '1m', target: s(150) }, { duration: '2m', target: s(150) },
      { duration: '1m', target: s(300) }, { duration: '2m', target: s(300) },
      { duration: '1m', target: 0 },
    ],
    pic: [
      { duration: '20s', target: s(500) }, { duration: '2m', target: s(500) },
      { duration: '30s', target: 0 },
    ],
  };
  if (!p[profil]) throw new Error(`PROFILE inconnu : ${profil} (fumee | palier | pic)`);
  return p[profil];
}

/** Profils en NOMBRE DE REQUÊTES PAR SECONDE (ramping-arrival-rate). */
export function etapesDebit(profil = PROFILE) {
  const p = {
    fumee: [{ duration: '30s', target: 3 }],
    palier: [
      { duration: '1m', target: s(10) }, { duration: '2m', target: s(10) },
      { duration: '1m', target: s(30) }, { duration: '2m', target: s(30) },
      { duration: '1m', target: s(60) }, { duration: '2m', target: s(60) },
      { duration: '30s', target: 0 },
    ],
    pic: [
      { duration: '10s', target: s(100) }, { duration: '1m', target: s(100) },
      { duration: '20s', target: 0 },
    ],
  };
  if (!p[profil]) throw new Error(`PROFILE inconnu : ${profil} (fumee | palier | pic)`);
  return p[profil];
}

function secondes(d) {
  const n = parseFloat(d);
  return d.endsWith('m') ? n * 60 : n;
}

/** Nombre approximatif d'itérations d'un profil en débit (pour dimensionner les données). */
export function iterationsPrevues(etapes) {
  let total = 0;
  let precedent = 0;
  for (const e of etapes) {
    total += secondes(e.duration) * ((precedent + e.target) / 2);
    precedent = e.target;
  }
  return Math.round(total);
}

/** Seuils communs : le test s'ARRÊTE SEUL si le serveur souffre (protège la production). */
export function seuilsProtection(extra = {}) {
  return {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '20s' }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '30s' }],
    ...extra,
  };
}

export function connexion(email, motDePasse) {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: motDePasse }), {
    headers: JSON_HEADERS, tags: { name: 'login' },
  });
  if (r.status !== 200) throw new Error(`Connexion impossible pour ${email} (code ${r.status})`);
  return r.json('accessToken');
}

export function evenementParSlug(slug) {
  const r = http.get(`${BASE}/api/public/events/${slug}`);
  if (r.status !== 200) throw new Error(`Événement introuvable (${r.status}) : ${slug}`);
  return r.json();
}

export const alea = (tableau) => tableau[Math.floor(Math.random() * tableau.length)];
