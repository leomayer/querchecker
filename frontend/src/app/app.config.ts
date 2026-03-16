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
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { Theme } from './features/settings/theme';
import { EventSourceServerService } from './shared/utils/event-source-server';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(),
    provideAnimations(),
    { provide: LOCALE_ID, useValue: 'de-AT' },
    provideAppInitializer(() => {
      const theme = inject(Theme);
      theme.darkMode(); // Trigger initialization
      inject(EventSourceServerService); // Open SSE connection eagerly at startup
    }),
  ],
};
