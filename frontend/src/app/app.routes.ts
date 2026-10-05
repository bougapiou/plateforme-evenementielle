import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./layout/public-layout.component').then((m) => m.PublicLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/public/events-public-list.component').then(
            (m) => m.EventsPublicListComponent,
          ),
        title: 'Événements — Plateforme Nationale des Événements',
      },
      {
        path: 'evenements/:slug',
        loadComponent: () =>
          import('./features/public/event-detail.component').then((m) => m.EventDetailComponent),
        title: 'Événement',
      },
      {
        // Entrée « Présence en direct » du menu public : les événements en cours.
        path: 'presence-en-direct',
        loadComponent: () =>
          import('./features/public/live-events.component').then((m) => m.LiveEventsComponent),
        title: 'Présence en direct',
      },
      {
        // Présence en direct d'un événement, sans connexion — lien partageable
        // pour un écran à l'entrée (le plein écran s'ouvre depuis la page).
        path: 'evenements/:slug/flux',
        loadComponent: () =>
          import('./features/public/public-attendance.component').then(
            (m) => m.PublicAttendanceComponent,
          ),
        title: 'Présence en direct',
      },
      {
        // Pointage des visiteurs : module à part entière, indépendant des événements — un
        // visiteur coche les stands (points de comptage) visités, sans lien avec la billetterie.
        path: 'pointage-visiteurs',
        loadComponent: () =>
          import('./features/pointage/pointage-select.component').then(
            (m) => m.PointageSelectComponent,
          ),
        title: 'Pointage des visiteurs',
      },
      {
        path: 'pointage-visiteurs/stats',
        loadComponent: () =>
          import('./features/pointage/pointage-stats.component').then(
            (m) => m.PointageStatsComponent,
          ),
        title: 'Pointage des visiteurs — statistiques',
      },
      {
        path: 'pointage-visiteurs/rapport',
        loadComponent: () =>
          import('./features/pointage/pointage-report.component').then(
            (m) => m.PointageReportComponent,
          ),
        title: 'Pointage des visiteurs — rapport global',
      },
      {
        path: 'pointage-visiteurs/stands/:standId',
        loadComponent: () =>
          import('./features/pointage/pointage-stand-stats.component').then(
            (m) => m.PointageStandStatsComponent,
          ),
        title: 'Pointage du stand',
      },
      {
        // Un visiteur signale son passage depuis la page propre à un stand (un lien/QR par
        // stand), et le classement par stand est public. (Module événementiel distinct, pour
        // les stands réellement réservés dans le cadre d'un événement.)
        path: 'evenements/:slug/stands/:standId/passage',
        loadComponent: () =>
          import('./features/frequentation/signaler-passage.component').then(
            (m) => m.SignalerPassageComponent,
          ),
        title: 'Signaler mon passage',
      },
      {
        // Sélection libre : le visiteur coche tous les stands visités en une fois (ou "Tout
        // sélectionner"), puis donne une identité facultative une seule fois pour l'ensemble.
        path: 'evenements/:slug/pointage/signaler',
        loadComponent: () =>
          import('./features/frequentation/signaler-passages.component').then(
            (m) => m.SignalerPassagesComponent,
          ),
        title: 'Signaler mes passages',
      },
      {
        path: 'evenements/:slug/pointage/stands/:standId',
        loadComponent: () =>
          import('./features/frequentation/stand-frequentation.component').then(
            (m) => m.StandFrequentationComponent,
          ),
        title: 'Pointage du stand',
      },
      {
        path: 'evenements/:slug/pointage',
        loadComponent: () =>
          import('./features/frequentation/frequentation-page.component').then(
            (m) => m.FrequentationPageComponent,
          ),
        title: 'Pointage des visiteurs',
      },
      {
        path: 'connexion',
        loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent),
        title: 'Connexion',
      },
      {
        path: 'inscription',
        loadComponent: () => import('./pages/register.component').then((m) => m.RegisterComponent),
        title: 'Créer un compte',
      },
      {
        path: 'finaliser-compte',
        loadComponent: () =>
          import('./pages/complete-account.component').then((m) => m.CompleteAccountComponent),
        title: 'Finaliser mon compte',
      },
      {
        path: 'mot-de-passe-oublie',
        loadComponent: () =>
          import('./pages/forgot-password.component').then((m) => m.ForgotPasswordComponent),
        title: 'Mot de passe oublié',
      },
      {
        path: 'mot-de-passe/reinitialiser',
        loadComponent: () =>
          import('./pages/reset-password.component').then((m) => m.ResetPasswordComponent),
        title: 'Nouveau mot de passe',
      },
      {
        path: 'scanner',
        loadComponent: () =>
          import('./features/public/qr-scanner.component').then((m) => m.QrScannerComponent),
        title: 'Scanner un QR code',
      },
      {
        path: 'retrouver-billet',
        loadComponent: () =>
          import('./features/public/find-ticket.component').then((m) => m.FindTicketComponent),
        title: 'Retrouver mon billet',
      },
    ],
  },
  {
    path: 'tableau-de-bord',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layout/dashboard-layout.component').then((m) => m.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/dashboard-overview.component').then((m) => m.DashboardOverviewComponent),
        title: 'Tableau de bord',
      },
      {
        path: 'evenements',
        loadComponent: () =>
          import('./features/events/events-list.component').then((m) => m.EventsListComponent),
        title: 'Mes événements',
      },
      {
        path: 'evenements/:id',
        loadComponent: () =>
          import('./features/events/event-editor.component').then((m) => m.EventEditorComponent),
        title: 'Événement',
      },
      {
        path: 'billets',
        loadComponent: () =>
          import('./features/tickets/my-tickets.component').then((m) => m.MyTicketsComponent),
        title: 'Mes billets',
      },
      {
        path: 'stands',
        loadComponent: () =>
          import('./features/stands/my-stands.component').then((m) => m.MyStandsComponent),
        title: 'Mes stands',
      },
      {
        path: 'inscriptions',
        loadComponent: () =>
          import('./features/registrations/my-registrations.component').then(
            (m) => m.MyRegistrationsComponent,
          ),
        title: 'Mes inscriptions',
      },
      {
        path: 'paiements',
        loadComponent: () =>
          import('./features/payments/my-payments.component').then((m) => m.MyPaymentsComponent),
        title: 'Mes paiements',
      },
      {
        path: 'factures',
        loadComponent: () =>
          import('./features/invoices/my-invoices.component').then((m) => m.MyInvoicesComponent),
        title: 'Mes factures',
      },
      {
        path: 'controle',
        canActivate: [authGuard],
        data: { permission: 'CHECKIN_SCAN' },
        loadComponent: () =>
          import('./features/checkin/scanner.component').then((m) => m.ScannerComponent),
        title: "Contrôle à la porte",
      },
      {
        path: 'structures',
        loadComponent: () =>
          import('./features/structures/structures-list.component').then(
            (m) => m.StructuresListComponent,
          ),
        title: 'Mes structures',
      },
      {
        path: 'structures/:id',
        loadComponent: () =>
          import('./features/structures/structure-detail.component').then(
            (m) => m.StructureDetailComponent,
          ),
        title: 'Structure',
      },
      {
        path: 'organisateur',
        loadComponent: () =>
          import('./features/organizer/organizer.component').then((m) => m.OrganizerComponent),
        title: 'Espace organisateur',
      },
      {
        path: 'admin/utilisateurs',
        canActivate: [authGuard],
        data: { permission: 'USER_READ' },
        loadComponent: () =>
          import('./features/admin/admin-users.component').then((m) => m.AdminUsersComponent),
        title: 'Utilisateurs',
      },
      {
        path: 'admin/structures',
        canActivate: [authGuard],
        data: { permission: 'STRUCTURE_READ' },
        loadComponent: () =>
          import('./features/admin/admin-structures.component').then(
            (m) => m.AdminStructuresComponent,
          ),
        title: 'Structures',
      },
      {
        path: 'admin/organisateurs',
        canActivate: [authGuard],
        data: { permission: 'ORGANIZER_MANAGE' },
        loadComponent: () =>
          import('./features/admin/admin-organizers.component').then(
            (m) => m.AdminOrganizersComponent,
          ),
        title: 'Organisateurs',
      },
      {
        path: 'admin/evenements',
        canActivate: [authGuard],
        data: { permission: 'EVENT_VALIDATE' },
        loadComponent: () =>
          import('./features/admin/admin-events.component').then((m) => m.AdminEventsComponent),
        title: 'Événements',
      },
      {
        path: 'presence',
        canActivate: [authGuard],
        data: { permission: 'CHECKIN_SCAN' },
        loadComponent: () =>
          import('./features/admin/admin-attendance.component').then(
            (m) => m.AdminAttendanceComponent,
          ),
        title: 'Présence / Flux',
      },
      {
        path: 'presence/billets',
        canActivate: [authGuard],
        data: { permission: 'CHECKIN_SCAN' },
        loadComponent: () =>
          import('./features/checkin/ticket-flow.component').then(
            (m) => m.TicketFlowComponent,
          ),
        title: 'Détail des billets',
      },
    ],
  },
  {
    // Écran de présence en plein écran, sans navbar ni sidebar — pour un
    // moniteur à l'entrée. Réservé aux mêmes personnes que la page ci-dessus
    // (organisateur, personnel de contrôle assigné, administrateur).
    path: 'presence/:eventId',
    canActivate: [authGuard],
    data: { permission: 'CHECKIN_SCAN' },
    loadComponent: () =>
      import('./features/checkin/presence-kiosk.component').then(
        (m) => m.PresenceKioskComponent,
      ),
    title: 'Présence — écran',
  },
  {
    // Même écran plein format, mais public (sans connexion) — pour un
    // événement déjà visible sur le site (voir /api/public/events/{slug}/attendance).
    path: 'presence-publique/:slug',
    loadComponent: () =>
      import('./features/public/public-presence-kiosk.component').then(
        (m) => m.PublicPresenceKioskComponent,
      ),
    title: 'Présence — écran',
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found.component').then((m) => m.NotFoundComponent),
    title: 'Page introuvable',
  },
];
