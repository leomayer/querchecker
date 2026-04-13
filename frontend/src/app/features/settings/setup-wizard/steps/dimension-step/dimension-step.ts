import { Component, input, inject, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';
import { SetupDimensionDto, SetupProviderDto, SetupFieldDto } from '../../../../../api';
import { SetupWizardStore } from '../../setup-wizard.store';

@Component({
  selector: 'app-dimension-step',
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatTabsModule, MatIconModule, MatRadioModule],
  templateUrl: './dimension-step.html',
  styleUrl: './dimension-step.scss',
  host: { 'class': 'dimension-step dimension-step-host' },
})
export class DimensionStep {
  readonly dimension = input.required<SetupDimensionDto>();
  protected readonly store = inject(SetupWizardStore);

  readonly selectedTabIndex = computed(() => {
    const dim = this.dimension();
    const selected = this.store.selectedProviders()[dim.name!];
    const idx = dim.providers?.findIndex(p => p.name === selected) ?? 0;
    return idx >= 0 ? idx : 0;
  });

  readonly isLocalLlmSelected = computed(() => {
    const dim = this.dimension();
    return dim.name === 'LLM' && this.store.selectedProviders()[dim.name!] === 'LOCAL';
  });

  onTabChange(index: number): void {
    // Tab switch navigates to view the provider config, does NOT change active selection
  }

  onSelectProvider(dimensionName: string, providerName: string): void {
    this.store.selectProvider(dimensionName, providerName);
  }

  onFieldChange(providerName: string, fieldKey: string, value: string): void {
    this.store.updateField(providerName, fieldKey, value);
  }

  getFieldValue(providerName: string, fieldKey: string): string {
    return this.store.getFieldValue(providerName, fieldKey);
  }
}
