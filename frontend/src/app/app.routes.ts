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
        loadComponent: () => import('./pages/home.component').then((m) => m.HomeComponent),
        title: 'Accueil — Plateforme Nationale des Événements',
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
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found.component').then((m) => m.NotFoundComponent),
    title: 'Page introuvable',
  },
];
