import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.token() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const analystGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAnalyst() ? true : inject(Router).createUrlTree(['/dashboard'], { queryParams: { denied: 1 } });
};
