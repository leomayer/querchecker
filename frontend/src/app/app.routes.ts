import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/listings/listings.component').then((m) => m.ListingsComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
