import { Injectable, signal } from '@angular/core';

export type FontSize = 'small' | 'medium' | 'large';

const FONT_SIZE_PX: Record<FontSize, string> = {
  small: '14px',
  medium: '16px',
  large: '18px',
};

const LS_FONT_SIZE = 'querchecker_font_size';
const LS_DARK_MODE = 'querchecker_dark_mode';

@Injectable({
  providedIn: 'root',
})
export class Theme {
  private readonly _storedDarkMode =
    localStorage.getItem(LS_DARK_MODE) !== null
      ? localStorage.getItem(LS_DARK_MODE) === 'true'
      : window.matchMedia('(prefers-color-scheme: dark)').matches;
  darkMode = signal(this._storedDarkMode);

  private readonly _storedFontSize =
    (localStorage.getItem(LS_FONT_SIZE) as FontSize | null) ?? 'medium';
  fontSize = signal<FontSize>(this._storedFontSize);

  constructor() {
    document.body.classList.toggle('dark-theme', this._storedDarkMode);
    this._applyFontSize(this._storedFontSize);
  }

  setDarkTheme(isDark: boolean): void {
    this.darkMode.set(isDark);
    localStorage.setItem(LS_DARK_MODE, String(isDark));
    document.body.classList.toggle('dark-theme', isDark);
  }

  setFontSize(size: FontSize): void {
    this.fontSize.set(size);
    localStorage.setItem(LS_FONT_SIZE, size);
    this._applyFontSize(size);
  }

  private _applyFontSize(size: FontSize): void {
    document.documentElement.style.fontSize = FONT_SIZE_PX[size];
  }
}
