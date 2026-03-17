import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DlExtractionTermDto } from '../../../../api/model/dlExtractionTermDto';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ExtractionStore } from '../../extraction.store';

interface TermGroup {
  modelName: string;
  terms: DlExtractionTermDto[];
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-item-research',
  imports: [MatIconModule, MatProgressSpinnerModule],
  templateUrl: './item-research.component.html',
  styleUrl: './item-research.component.scss',
})
export class ItemResearchComponent {
  readonly detail = input.required<WhDetailDto>();

  private readonly extractionStore = inject(ExtractionStore);

  protected readonly state = computed<'idle' | 'loading' | 'done'>(() => {
    const id = this.detail().itemTextId;
    if (id == null) return 'idle';
    return id in this.extractionStore.results() ? 'done' : 'loading';
  });

  protected readonly termGroups = computed<TermGroup[]>(() => {
    const id = this.detail().itemTextId;
    if (id == null) return [];
    return this.groupByModel(this.extractionStore.results()[id] ?? []);
  });

  private groupByModel(terms: DlExtractionTermDto[]): TermGroup[] {
    const map = new Map<string, DlExtractionTermDto[]>();
    for (const t of terms) {
      const key = t.modelName ?? 'Unbekannt';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(t);
    }
    return Array.from(map.entries()).map(([modelName, ts]) => ({ modelName, terms: ts }));
  }

  protected confidencePct(confidence: number | undefined): string {
    if (confidence == null) return '';
    return Math.round(confidence * 100) + '%';
  }

  protected confidenceClass(confidence: number | undefined): string {
    if (confidence == null) return '';
    if (confidence >= 0.7) return 'conf-high';
    if (confidence >= 0.4) return 'conf-mid';
    return 'conf-low';
  }
}
