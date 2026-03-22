import { Component, inject, signal } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Theme } from '../theme';

@Component({
  selector: 'app-theme-toggle',
  imports: [MatSlideToggleModule],
  templateUrl: './theme-toggle.html',
  styleUrl: './theme-toggle.scss',
})
export class ThemeToggle {
  theme = inject(Theme);
}
