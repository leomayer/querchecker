import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Theme } from '../theme';

@Component({
  selector: 'app-theme-toggle',
  imports: [MatIconModule, MatButtonModule, MatCardModule, MatSlideToggleModule],
  templateUrl: './theme-toggle.html',
  styleUrl: './theme-toggle.scss',
})
export class ThemeToggle {
  theme = inject(Theme);
}
