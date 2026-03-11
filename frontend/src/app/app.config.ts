import {
  APP_INITIALIZER,
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';
import { Theme } from './features/settings/theme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(),
    provideAnimationsAsync(),
    { provide: LOCALE_ID, useValue: 'de-AT' },
    provideAppInitializer(() => {
      const theme = inject(Theme);
      theme.darkMode(); // Trigger initialization
    }),
  ],
};
