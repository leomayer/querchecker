import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import localeDeAt from '@angular/common/locales/de-AT';
import { AppComponent } from './app/app';
import { appConfig } from './app/app.config';

registerLocaleData(localeDe);
registerLocaleData(localeDeAt);

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
