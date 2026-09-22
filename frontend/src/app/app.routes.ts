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
        // Présence en direct, sans connexion — un lien dédié par événement
        // (pas de sélecteur), pour un écran à l'entrée ou un partage public.
        path: 'evenements/:slug/flux',
        loadComponent: () =>
          import('./features/public/public-attendance.component').then(
            (m) => m.PublicAttendanceComponent,
          ),
        title: 'Présence en direct',
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
        title: "Contrôle à l'entrée",
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
