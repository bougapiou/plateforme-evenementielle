import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/connexion'], {
      queryParams: { redirect: route.pathFromRoot.map((r) => r.url.join('/')).join('/') },
    });
  }

  const required = route.data?.['permission'] as string | undefined;
  if (required && !auth.hasPermission(required)) {
    return router.createUrlTree(['/tableau-de-bord']);
  }
  return true;
};
