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
        path: 'connexion',
        loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent),
        title: 'Connexion',
      },
      {
        path: 'inscription',
        loadComponent: () => import('./pages/register.component').then((m) => m.RegisterComponent),
        title: 'Créer un compte',
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
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found.component').then((m) => m.NotFoundComponent),
    title: 'Page introuvable',
  },
];
