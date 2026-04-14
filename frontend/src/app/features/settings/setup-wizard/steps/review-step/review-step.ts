import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SnackService } from '../../../../../shared/services/snack.service';
import { SetupWizardStore } from '../../setup-wizard.store';

@Component({
  selector: 'app-review-step',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './review-step.html',
  styleUrl: './review-step.scss',
})
export class ReviewStep {
  protected readonly store = inject(SetupWizardStore);
  private readonly http = inject(HttpClient);
  private readonly snack = inject(SnackService);

  saveAndRestart(): void {
    const payload = this.store.buildSavePayload();
    this.store.saving.set(true);
    this.store.saveError.set(null);

    this.http.post('/api/provider-setup/save', payload).subscribe({
      next: () => {
        this.store.saving.set(false);
        this.restart();
      },
      error: () => {
        this.store.saving.set(false);
        this.store.saveError.set('Speichern fehlgeschlagen');
      },
    });
  }

  restart(): void {
    this.http.post('/api/admin/restart', null).subscribe({
      next: () => {
        this.snack.info(
          'Neustart folgt durch Prozess-Manager (Docker, systemd, …).',
          'Server wird heruntergefahren',
          8000,
        );
      },
      error: () => {
        // Erwartbar — Server beendet sich, Verbindung bricht ab
        this.snack.info('Prüfe deinen Prozess-Manager für Neustart.', 'Shutdown ausgelöst', 5000);
      },
    });
  }

  downloadSecrets(): void {
    const payload = this.store.buildSavePayload();
    const content = this.buildSecretsYaml(payload.secrets ?? {});
    this.downloadFile('secrets.yml', content);
  }

  downloadConfig(): void {
    this.snack.info(
      'Wird in einer späteren Version unterstützt.',
      'querchecker.yml Download',
      5000,
    );
  }

  private buildSecretsYaml(secrets: Record<string, string>): string {
    const lines: string[] = [
      '# secrets.yml — API-Keys für Querchecker',
      '# NIEMALS in Git einchecken.',
      '',
      'querchecker:',
      '  api:',
      '    limits:',
      '      brave:',
      `        api-key: ${secrets['BRAVE_PLACEHOLDER'] ?? 'BRAVE_PLACEHOLDER'}`,
      '      groq:',
      `        api-key: ${secrets['GROQ_PLACEHOLDER'] ?? 'GROQ_PLACEHOLDER'}`,
      '      openrouter:',
      `        api-key: ${secrets['OPENROUTER_PLACEHOLDER'] ?? 'OPENROUTER_PLACEHOLDER'}`,
      // credentials-path ist kein Secret — wird in querchecker.yml konfiguriert, nicht hier
    ];
    return lines.join('\n') + '\n';
  }

  private downloadFile(filename: string, content: string): void {
    const blob = new Blob([content], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
