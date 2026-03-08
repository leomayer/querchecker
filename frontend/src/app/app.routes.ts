import { Routes } from '@angular/router';
import { listingsGuard } from './features/wh-search/listings.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: 'wh-listings',
    canActivate: [listingsGuard],
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: 'wh-listings/:id',
    canActivate: [listingsGuard],
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
