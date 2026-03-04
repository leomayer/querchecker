import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/wh-search/wh-search.component').then((m) => m.WhSearchComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
