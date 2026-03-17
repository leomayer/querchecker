import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { DlExtractionService } from '../../../core/dl-extraction.service';

@Component({
  selector: 'app-dl-extraction-settings',
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatSliderModule],
  templateUrl: './dl-extraction-settings.html',
  styleUrl: './dl-extraction-settings.scss',
})
export class DlExtractionSettings implements OnInit {
  private readonly dlService = inject(DlExtractionService);

  contextMaxTokens = signal(512);
  saving = signal(false);
  saveResult = signal<string | null>(null);

  ngOnInit(): void {
    this.dlService.getSettings().subscribe({
      next: (s) => this.contextMaxTokens.set(s.contextMaxTokens),
    });
  }

  save(): void {
    this.saving.set(true);
    this.saveResult.set(null);
    this.dlService.updateSettings({ contextMaxTokens: this.contextMaxTokens() }).subscribe({
      next: () => {
        this.saveResult.set('Gespeichert.');
        this.saving.set(false);
      },
      error: () => {
        this.saveResult.set('Fehler beim Speichern.');
        this.saving.set(false);
      },
    });
  }
}
