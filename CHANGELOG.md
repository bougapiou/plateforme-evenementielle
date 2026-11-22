# Journal des versions

Format : une entrée par mise en production, du plus récent au plus ancien
(TECH-06). Les références renvoient au « cahier des évolutions » de novembre 2026.

## [Non publié] — évolutions du cahier (SN 2026)

### Site public et participants
- Nom de la structure organisatrice affiché (P1-01) ; événements de test masquables,
  terminés depuis plus de 30 jours retirés de la vitrine (P1-02).
- Programme : activités sur plusieurs jours affichées chaque jour (« En continu »),
  filtres jour / salle / type / thème et recherche (PART-07, PART-01).
- Bouton « S'inscrire » par session, sélection de plusieurs sessions et un seul
  formulaire, billets de session avec date et heure (P1-04, PART-03).
- « Mon programme » exportable en .ics / Google Agenda (PART-02) ; fiches
  intervenants avec leurs sessions, partenaires par niveau (PART-06).
- « Retrouver mon billet » par code SMS (PART-04) ; lien de partage avec aperçu
  (Open Graph) et partage WhatsApp (PART-08) ; format en ligne / hybride avec lien
  de diffusion réservé aux inscrits (PART-10) ; PWA installable (PART-11).
- Page « Confidentialité et mentions légales » liée au formulaire (SEC-07).

### Organisateurs
- Import du programme Excel / CSV avec aperçu et rapport d'erreurs (ORG-01).
- Champs description, thème / piste, nouveaux types d'activité (ORG-03, ORG-04).
- Alerte de conflit de salle (ORG-05) ; duplication en édition suivante (ORG-07).

### Contrôle d'accès
- Scanner mobile hors ligne avec synchronisation sans doublon (CTRL-01) ; signal
  sonore (CTRL-02) ; inscription sur place (CTRL-03) ; QR d'auto-enregistrement
  par salle (CTRL-04) ; agents affectés à des salles, journal par agent (CTRL-05).

### Statistiques
- Tableau par activité complet et triable, export CSV / Excel, liste nominative
  avec présence et heure d'entrée (P1-05, STAT-01, STAT-02) ; rapport de bilan
  PDF (STAT-04).

### Sécurité et exploitation
- Mots de passe de 12 caractères, refus des mots de passe courants, verrouillage
  après 5 échecs (SEC-01) ; double authentification TOTP (SEC-02).
- Rôles valideur, gestionnaire de programme, lecteur des statistiques (SEC-04) ;
  journal d'audit consultable et non modifiable (SEC-05) ; limitation des
  inscriptions par IP (SEC-06) ; anonymisation après 12 mois (SEC-08).
- Scripts de sauvegarde / restauration / supervision (TECH-01, TECH-02) ;
  intégration continue (TECH-05) ; documentation des rôles (TECH-04).

### Migrations Flyway
- V27 à V31.
