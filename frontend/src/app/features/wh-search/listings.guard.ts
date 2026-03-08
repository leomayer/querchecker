import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SearchStore } from './search.store';

export const listingsGuard: CanActivateFn = () => {
  const store = inject(SearchStore);
  const router = inject(Router);
  return store.listings().length > 0 || router.createUrlTree(['/']);
};
