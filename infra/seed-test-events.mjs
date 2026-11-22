// Données de test : événements GRATUITS + programme complet de la Semaine du Numérique.
// Passe par l'API (création organisateur → soumission → validation admin → publication
// → ouverture des inscriptions), donc aucune modification du code ni de la base à la main.
//
// Usage :   node infra/seed-test-events.mjs            (API sur http://localhost:8080)
//           API=http://autre:8080 node infra/seed-test-events.mjs
// Idempotent : un événement déjà présent (même nom) est ignoré.
//
// Comptes utilisés (données de démo, DEMO_DATA=true) :
//   organisateur@plateforme.bf / Demo!2026     admin@plateforme.bf / ChangeMe!2026

const API = (process.env.API ?? 'http://localhost:8080') + '/api';
const ORGA = { email: process.env.ORGA_EMAIL ?? 'organisateur@plateforme.bf', password: process.env.ORGA_PASSWORD ?? 'Demo!2026' };
const ADMIN = { email: process.env.ADMIN_EMAIL ?? 'admin@plateforme.bf', password: process.env.ADMIN_PASSWORD ?? 'ChangeMe!2026' };

// Ouagadougou = UTC+0 : une heure locale s'écrit directement en Z.
const at = (date, hhmm) => `${date}T${hhmm}:00Z`;

async function call(token, method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : null;
}
const login = async (c) => (await call(null, 'POST', '/auth/login', c)).accessToken;

function SDN_TICKETS() {
  return [
    {
      nom: 'Entrée visiteur',
      description: 'Accès au site : exposition, cérémonies, lancement du hackathon et soirée culturelle. Les conférences, panels et ateliers se réservent à part.',
      prixMontant: 0, portee: 'ACTIVITE', quantiteTotale: 5000, formulaireRequis: true, identiteRequise: 'NOM_ET_PRENOM',
      sessions: (a) => a.acces === 'SANS_BILLET' && a.typeActivite !== 'PAUSE',
    },
    {
      nom: 'Pass Conférences',
      description: 'Un seul billet pour toutes les conférences de la Grande salle.',
      prixMontant: 0, portee: 'ACTIVITE', quantiteTotale: 800, formulaireRequis: true, identiteRequise: 'NOM_ET_PRENOM',
      sessions: (a) => a.typeActivite === 'CONFERENCE',
    },
  ];
}

// --------------------------------------------------------------------------------------
// Semaine du Numérique — du mardi 24 au samedi 28 novembre 2026
// --------------------------------------------------------------------------------------
const SDN = {
  event: {
    nom: 'Semaine du Numérique 2026',
    sigle: 'SN 2026',
    categorie: 'semaine-thematique',
    descriptionCourte: 'Cinq jours de conférences, ateliers, hackathon et rencontres autour de la transformation numérique. Entrée gratuite.',
    descriptionDetaillee:
      "La Semaine du Numérique réunit administrations, entreprises, startups, chercheurs, étudiants et grand public autour de la transformation numérique du Burkina Faso.\n\n" +
      "Au programme : conférences et panels de haut niveau, ateliers pratiques et formations gratuites, hackathon Faso Innov, rencontres B2B entre startups et investisseurs, espace d'exposition et soirée culturelle.\n\n" +
      "L'entrée est gratuite sur inscription. Certains ateliers ont un nombre de places limité : réservez votre place directement dans le programme.",
    dateDebut: at('2026-11-24', '08:00'),
    dateFin: at('2026-11-28', '18:00'),
    lieu: 'Palais des Sports de Ouaga 2000',
    adresse: 'Avenue Pascal Zagré, Ouaga 2000',
    ville: 'Ouagadougou',
    capaciteMax: 5000,
    contactEmail: 'contact@plateforme.bf',
    contactTelephone: '+226 25 00 00 00',
    conditionsParticipation: "Entrée gratuite sur présentation du billet électronique (QR code). Pièce d'identité recommandée. Les ateliers à places limitées nécessitent une réservation dans le programme.",
    hasActivities: true,
    standsActifs: false,
  },
  // Formule « Pass + billets à l'unité » :
  //  - Entrée visiteur : le site, l'exposition, les cérémonies, la soirée (PAS les conférences)
  //  - Pass Conférences : un seul QR pour toutes les conférences
  //  - chaque conférence / panel / atelier se réserve aussi à l'unité (« Réserver cette session »)
  // `sessions` désigne les activités couvertes ; elles sont résolues après la création du programme.
  tickets: SDN_TICKETS(),
  programme: [
    // Mardi 24 — ouverture
    ['2026-11-24', '08:00', '09:00', 'Accueil et enregistrement des participants', 'AUTRE', 'SANS_BILLET', 'Hall d’entrée', null, 'Retrait des badges et orientation.'],
    ['2026-11-24', '09:00', '10:30', 'Cérémonie d’ouverture officielle', 'CEREMONIE', 'SANS_BILLET', 'Grande salle', 'Autorités gouvernementales', 'Discours d’ouverture et lancement officiel de la Semaine du Numérique 2026.'],
    ['2026-11-24', '11:00', '12:30', 'Conférence inaugurale : souveraineté numérique et modernisation de l’administration', 'CONFERENCE', 'GRATUIT', 'Grande salle', 'Direction générale de l’économie numérique', 'Comment le numérique transforme la relation entre l’État et les citoyens.', 800],
    ['2026-11-24', '12:30', '14:00', 'Pause déjeuner', 'PAUSE', 'SANS_BILLET', 'Espace restauration', null, null],
    ['2026-11-24', '14:00', '15:30', 'Panel : identité numérique et services publics en ligne', 'PANEL', 'GRATUIT', 'Salle A', 'Représentants d’administrations et d’opérateurs', 'Démarches en ligne, état civil numérique, interopérabilité des systèmes.', 300],
    ['2026-11-24', '16:00', '17:30', 'Visite guidée de l’espace d’exposition', 'AUTRE', 'SANS_BILLET', 'Hall d’exposition', null, 'Découverte des solutions des startups et entreprises exposantes.'],

    // Mercredi 25 — IA et compétences
    ['2026-11-25', '09:00', '10:30', 'Conférence : intelligence artificielle, opportunités pour l’Afrique de l’Ouest', 'CONFERENCE', 'GRATUIT', 'Grande salle', 'Chercheurs et universitaires', 'Usages concrets de l’IA en santé, éducation et agriculture.', 800],
    ['2026-11-25', '11:00', '12:30', 'Atelier : créer son site web sans coder', 'ATELIER', 'GRATUIT', 'Salle informatique 1', 'Formateurs certifiés', 'Atelier pratique — apportez votre ordinateur portable.', 40],
    ['2026-11-25', '12:30', '14:00', 'Pause déjeuner', 'PAUSE', 'SANS_BILLET', 'Espace restauration', null, null],
    ['2026-11-25', '14:00', '16:00', 'Formation : cybersécurité, les bons réflexes', 'FORMATION', 'GRATUIT', 'Salle B', 'Agence nationale de sécurité des systèmes d’information', 'Mots de passe, hameçonnage, sauvegardes : se protéger au quotidien.', 60],
    ['2026-11-25', '16:30', '18:00', 'Table ronde : inclusion numérique des femmes et des jeunes filles', 'TABLE_RONDE', 'GRATUIT', 'Salle A', 'Associations et entrepreneures du numérique', null, 300],

    // Jeudi 26 — entrepreneuriat
    ['2026-11-26', '08:30', '10:00', 'Lancement du hackathon Faso Innov', 'ATELIER', 'SANS_BILLET', 'Espace hackathon', 'Comité d’organisation', '48 heures pour concevoir une solution numérique au service des citoyens.'],
    ['2026-11-26', '10:30', '12:00', 'Conférence : paiement mobile et inclusion financière', 'CONFERENCE', 'GRATUIT', 'Grande salle', 'Acteurs de la fintech', null, 800],
    ['2026-11-26', '14:00', '15:30', 'Panel : startups burkinabè, financer la croissance', 'PANEL', 'GRATUIT', 'Salle A', 'Investisseurs et fondateurs de startups', null, 300],
    ['2026-11-26', '16:00', '18:00', 'Rencontres B2B startups – investisseurs', 'NETWORKING', 'GRATUIT', 'Espace networking', null, 'Rendez-vous de 15 minutes entre porteurs de projets et investisseurs.', 120],

    // Vendredi 27 — numérique et territoires
    ['2026-11-27', '09:00', '10:30', 'Conférence : agriculture connectée et données climatiques', 'CONFERENCE', 'GRATUIT', 'Grande salle', 'Experts en agronomie et données', null, 800],
    ['2026-11-27', '11:00', '12:30', 'Atelier jeunes : initiation à la programmation', 'ATELIER', 'GRATUIT', 'Salle informatique 2', 'Clubs de programmation', 'Pour les 12–18 ans. Premiers pas avec Scratch et Python.', 50],
    ['2026-11-27', '14:00', '15:30', 'Formation : données ouvertes et transparence', 'FORMATION', 'GRATUIT', 'Salle B', null, 'Publier, trouver et réutiliser les données publiques.', 60],
    ['2026-11-27', '19:00', '22:00', 'Soirée culturelle et concert', 'SPECTACLE', 'SANS_BILLET', 'Esplanade', 'Artistes burkinabè', 'Musique, danse et projection de courts métrages.'],

    // Samedi 28 — clôture
    ['2026-11-28', '09:00', '12:00', 'Finale du hackathon et présentation des projets', 'CEREMONIE', 'GRATUIT', 'Grande salle', 'Jury du hackathon', null, 800],
    ['2026-11-28', '15:00', '17:00', 'Remise des prix et cérémonie de clôture', 'CEREMONIE', 'SANS_BILLET', 'Grande salle', 'Autorités et partenaires', null],
  ],
};

// --------------------------------------------------------------------------------------
// Autres événements gratuits (couvrent les différents parcours de test)
// --------------------------------------------------------------------------------------
const AUTRES = [
  {
    // En cours aujourd'hui → « Présence en direct », événement EN_COURS
    event: {
      nom: 'Exposition « Regards sur le Faso »', categorie: 'exposition',
      descriptionCourte: 'Exposition photographique gratuite sur le patrimoine et la vie quotidienne au Burkina Faso.',
      descriptionDetaillee: 'Une centaine de photographies d’auteurs burkinabè. Visite libre, ouverte à tous, tous les jours de 9 h à 18 h.',
      dateDebut: at('2026-11-20', '09:00'), dateFin: at('2026-11-29', '18:00'),
      lieu: 'Musée national', adresse: 'Boulevard de l’Indépendance', ville: 'Ouagadougou',
      hasActivities: false, standsActifs: false,
    },
    // Billet sans formulaire : téléphone seul (parcours « en un clic »)
    tickets: [{ nom: 'Visite libre', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 2000, formulaireRequis: false }],
  },
  {
    event: {
      nom: 'Conférence : l’IA au service des collectivités', categorie: 'conference',
      descriptionCourte: 'Conférence-débat gratuite sur les usages de l’intelligence artificielle dans les communes.',
      descriptionDetaillee: 'Retours d’expérience de communes pilotes, présentation d’outils et débat avec le public.',
      dateDebut: at('2026-11-25', '17:00'), dateFin: at('2026-11-25', '19:30'),
      lieu: 'Institut français', adresse: 'Avenue de la Liberté', ville: 'Bobo-Dioulasso',
      hasActivities: false, standsActifs: false,
    },
    // Plusieurs catégories gratuites → choix du billet
    tickets: [
      { nom: 'Grand public', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 250, formulaireRequis: true, identiteRequise: 'NOM_ET_PRENOM' },
      { nom: 'Élus et agents des collectivités', description: 'Places réservées, accès aux premiers rangs.', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 50, formulaireRequis: true, identiteRequise: 'NOM_ET_PRENOM' },
    ],
  },
  {
    event: {
      nom: 'Atelier cybersécurité pour les PME', categorie: 'atelier',
      descriptionCourte: 'Atelier pratique gratuit, places limitées : protéger son entreprise contre les cyberattaques.',
      descriptionDetaillee: 'Diagnostic rapide, sauvegardes, gestion des accès et réaction en cas d’incident. Apportez votre ordinateur portable.',
      dateDebut: at('2026-11-30', '09:00'), dateFin: at('2026-11-30', '13:00'),
      lieu: 'Chambre de commerce — antenne régionale', ville: 'Koudougou',
      hasActivities: false, standsActifs: false,
    },
    // Petit quota + formulaire « nom seul »
    tickets: [{ nom: 'Participant', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 30, limiteParUtilisateur: 1, formulaireRequis: true, identiteRequise: 'NOM_SEUL' }],
  },
  {
    event: {
      nom: 'Forum de l’emploi des jeunes', categorie: 'forum',
      descriptionCourte: 'Rencontrez recruteurs et organismes de formation. Entrée gratuite.',
      descriptionDetaillee: 'Stands de recruteurs, ateliers CV et entretien, conférences sur les métiers d’avenir.',
      dateDebut: at('2026-12-03', '08:30'), dateFin: at('2026-12-03', '17:00'),
      lieu: 'Maison de la Jeunesse', ville: 'Ouagadougou',
      hasActivities: true, standsActifs: false,
    },
    tickets: [{ nom: 'Entrée gratuite', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 1500, formulaireRequis: true, identiteRequise: 'NOM_ET_PRENOM' }],
    programme: [
      ['2026-12-03', '08:30', '09:30', 'Ouverture du forum', 'CEREMONIE', 'SANS_BILLET', 'Salle polyvalente', null, null],
      ['2026-12-03', '10:00', '11:30', 'Atelier : réussir son CV', 'ATELIER', 'GRATUIT', 'Salle 1', 'Conseillers emploi', null, 40],
      ['2026-12-03', '11:30', '12:30', 'Conférence : les métiers d’avenir', 'CONFERENCE', 'SANS_BILLET', 'Salle polyvalente', null, null],
      ['2026-12-03', '14:00', '16:30', 'Job dating avec les recruteurs', 'NETWORKING', 'GRATUIT', 'Hall', null, 'Entretiens de 10 minutes — apportez plusieurs CV.', 200],
    ],
  },
  {
    event: {
      nom: 'Festival des arts de la rue', categorie: 'festival',
      descriptionCourte: 'Deux jours de spectacles gratuits en plein air : musique, danse, théâtre et contes.',
      descriptionDetaillee: 'Programmation familiale en plein air, ouverte à tous.',
      dateDebut: at('2026-12-12', '15:00'), dateFin: at('2026-12-13', '23:00'),
      lieu: 'Place de la Nation', ville: 'Ouagadougou',
      hasActivities: true, standsActifs: false,
    },
    tickets: [{ nom: 'Entrée gratuite', prixMontant: 0, portee: 'EVENEMENT', quantiteTotale: 10000, formulaireRequis: false }],
    programme: [
      ['2026-12-12', '15:00', '17:00', 'Parade d’ouverture', 'SPECTACLE', 'SANS_BILLET', 'Avenue Kwame Nkrumah', null, null],
      ['2026-12-12', '18:00', '20:00', 'Contes et théâtre pour enfants', 'SPECTACLE', 'SANS_BILLET', 'Scène B', null, null],
      ['2026-12-12', '20:30', '23:00', 'Concert de la soirée', 'SPECTACLE', 'SANS_BILLET', 'Grande scène', null, null],
      ['2026-12-13', '16:00', '18:00', 'Battle de danse', 'SPECTACLE', 'SANS_BILLET', 'Scène B', null, null],
      ['2026-12-13', '20:00', '23:00', 'Concert de clôture', 'SPECTACLE', 'SANS_BILLET', 'Grande scène', null, null],
    ],
  },
];

/** Corps de requête d'un billet ; `sessions` (filtre) → `activityIds`. */
function ticketPayload(t, ordre, activites) {
  const { sessions, ...rest } = t;
  const body = { devise: 'XOF', actif: true, ordre, ...rest };
  if (sessions) body.activityIds = activites.filter(sessions).map((a) => a.id);
  return body;
}

/**
 * Semaine du Numérique déjà créée avec l'ancienne formule (« Pass 5 jours » qui ouvrait tout) :
 * on la passe en formule « Entrée visiteur + Pass Conférences + billets à l'unité ».
 */
async function migrerSdn(orga, mine) {
  const ev = (mine.content ?? mine).find((e) => e.nom === SDN.event.nom);
  if (!ev) return;
  const tickets = await call(orga, 'GET', `/events/${ev.id}/tickets`);
  const ancien = tickets.find((t) => t.nom === 'Entrée libre — Pass 5 jours');
  if (!ancien) return;
  const activites = await call(orga, 'GET', `/events/${ev.id}/activities`);
  const [visiteur, pass] = SDN_TICKETS();
  // L'ancien billet devient « Entrée visiteur » (les éventuels billets déjà émis restent valables).
  await call(orga, 'PUT', `/events/${ev.id}/tickets/${ancien.id}`, ticketPayload(visiteur, 0, activites));
  if (!tickets.some((t) => t.nom === pass.nom)) {
    await call(orga, 'POST', `/events/${ev.id}/tickets`, ticketPayload(pass, 1, activites));
  }
  console.log(`~ formule mise à jour : ${SDN.event.nom} (Entrée visiteur + Pass Conférences + billets à l'unité)`);
}

// --------------------------------------------------------------------------------------
async function main() {
  const orga = await login(ORGA);
  const admin = await login(ADMIN);
  const categories = await call(null, 'GET', '/public/event-categories');
  const catId = (slug) => categories.find((c) => c.slug === slug)?.id;
  const mine = await call(orga, 'GET', '/events/mine?size=200');
  const existants = new Set((mine.content ?? mine).map((e) => e.nom));
  await migrerSdn(orga, mine);

  for (const def of [SDN, ...AUTRES]) {
    const { categorie, ...fields } = def.event;
    if (existants.has(fields.nom)) {
      console.log(`= déjà présent : ${fields.nom}`);
      continue;
    }
    const now = new Date().toISOString();
    const ev = await call(orga, 'POST', '/events', {
      pays: 'Burkina Faso',
      standsParticuliers: false,
      validationInscription: false,
      inscriptionDebut: now,
      inscriptionFin: fields.dateFin,
      ...fields,
      categoryId: catId(categorie),
    });

    // Programme d'abord : les pass multi-activités le référencent.
    const activites = [];
    for (const [i, a] of (def.programme ?? []).entries()) {
      const [jour, debut, fin, titre, type, acces, salle, intervenant, description, capacite] = a;
      activites.push(await call(orga, 'POST', `/events/${ev.id}/activities`, {
        titre, typeActivite: type, acces, salle,
        dateDebut: at(jour, debut), dateFin: at(jour, fin),
        intervenant: intervenant ?? undefined, description: description ?? undefined,
        capacite: capacite ?? undefined, ordre: i,
      }));
    }

    for (const [i, t] of def.tickets.entries()) {
      await call(orga, 'POST', `/events/${ev.id}/tickets`, ticketPayload(t, i, activites));
    }

    await call(orga, 'POST', `/events/${ev.id}/submit`);
    await call(admin, 'POST', `/events/${ev.id}/validate`);
    await call(orga, 'POST', `/events/${ev.id}/publish`);
    await call(orga, 'POST', `/events/${ev.id}/open-registrations`);
    console.log(`+ créé et publié : ${fields.nom} (${def.tickets.length} billet(s), ${(def.programme ?? []).length} activité(s)) → /evenements/${ev.slug}`);
  }
}

main().catch((e) => {
  console.error('ÉCHEC :', e.message);
  process.exit(1);
});
