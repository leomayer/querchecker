import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class Theme {
  darkMode = signal(window.matchMedia('(prefers-color-scheme: dark)').matches);
  constructor() {
    document.body.classList.toggle('dark-theme', this.darkMode());
  }

  setDarkTheme(isDark: boolean): void {
    this.darkMode.set(isDark);
    document.body.classList.toggle('dark-theme', this.darkMode());
  }
}
