import { Routes } from '@angular/router';
import { AppRoutePath } from './core/app-route-paths';
import { listingsGuard } from './features/wh-search/listings.guard';

export const routes: Routes = [
  {
    path: AppRoutePath.SEARCH,
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: AppRoutePath.LISTINGS,
    canActivate: [listingsGuard],
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: `${AppRoutePath.DETAIL}/:id`,
    canActivate: [listingsGuard],
    loadComponent: () =>
      import('./features/wh-search/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
  },
  {
    path: AppRoutePath.SETTINGS,
    loadComponent: () =>
      import('./features/settings/settings.component').then(
        (m) => m.SettingsComponent,
      ),
  },
  {
    path: '**',
    redirectTo: AppRoutePath.SEARCH,
  },
];
