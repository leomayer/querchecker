import { Component, effect, inject, output, viewChild } from '@angular/core';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
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
}
