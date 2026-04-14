import { Component, effect, inject, output, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SnackService } from '../../../shared/services/snack.service';
import { MoveableDialogHeaderComponent } from '../../../shared/components/moveable-dialog-header/moveable-dialog-header';
import { SetupWizardStore } from './setup-wizard.store';
import { DimensionStep } from './steps/dimension-step/dimension-step';
import { ReviewStep } from './steps/review-step/review-step';

@Component({
  selector: 'app-setup-wizard',
  imports: [
    MatStepperModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoveableDialogHeaderComponent,
    DimensionStep,
    ReviewStep,
  ],
  providers: [SetupWizardStore],
  templateUrl: './setup-wizard.html',
  styleUrl: './setup-wizard.scss',
})
export class SetupWizard {
  protected readonly store = inject(SetupWizardStore);
  protected readonly stepper = viewChild<MatStepper>('stepper');
  readonly closeWizard = output<void>();
  private readonly dialogRef = inject(MatDialogRef<SetupWizard>, { optional: true });
  private readonly http = inject(HttpClient);
  private readonly snack = inject(SnackService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      const data = this.store.initData();
      if (data) {
        this.store.initFromData(data);
      }
    });
  }

  onClose(): void {
    this.closeWizard.emit();
    this.dialogRef?.close();
  }

  isOnFinalStep(stepper: MatStepper): boolean {
    const totalSteps = this.store.dimensions().length + 1; // +1 for Abschluss step
    return stepper.selectedIndex === totalSteps - 1;
  }

  saveAndRestart(): void {
    const payload = this.store.buildSavePayload();
    this.store.saving.set(true);
    this.store.saveError.set(null);

    this.http.post('/api/provider-setup/save', payload).subscribe({
      next: () => {
        this.store.saving.set(false);
        this.snack.info(
          'Verbindung wird automatisch wiederhergestellt.',
          'Server wird heruntergefahren',
          8000,
        );
        this.closeWizard.emit();
        this.dialogRef?.close();
        // Navigate to home after dialog closes
        this.router.navigate(['/']);
      },
      error: () => {
        this.store.saving.set(false);
        this.store.saveError.set('Speichern fehlgeschlagen');
      },
    });
  }

  downloadSecrets(): void {
    const payload = this.store.buildSavePayload();
    const content = this.buildSecretsYaml(payload.secrets ?? {});
    this.downloadFile('secrets.yml', content);
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
      '    google-discovery:',
      `      credentials-path: ${secrets['GOOGLE_CREDENTIALS_PLACEHOLDER'] ?? 'GOOGLE_CREDENTIALS_PLACEHOLDER'}`,
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
