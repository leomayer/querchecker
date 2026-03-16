import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DlExtractionTermDto } from '../../../../api/model/dlExtractionTermDto';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { DlExtractionService } from '../../../../core/dl-extraction.service';
import { AppSseEventName, DlExtractionDonePayload } from '../../../../core/sse-events';
import { EventSourceServerService } from '../../../../shared/utils/event-source-server';

interface TermGroup {
  modelName: string;
  terms: DlExtractionTermDto[];
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-item-research',
  imports: [MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './item-research.component.html',
  styleUrl: './item-research.component.scss',
})
export class ItemResearchComponent implements OnInit {
  readonly detail = input.required<WhDetailDto>();

  protected readonly state = signal<'idle' | 'loading' | 'done'>('idle');
  protected readonly termGroups = signal<TermGroup[]>([]);

  private readonly dlService = inject(DlExtractionService);
  private readonly destroyRef = inject(DestroyRef);
  // Cast to typed event service — single shared SSE connection
  private readonly sseService = inject(
    EventSourceServerService,
  ) as EventSourceServerService<AppSseEventName, DlExtractionDonePayload>;

  private readonly onDone = (payload: DlExtractionDonePayload): void => {
    const currentId = this.detail().itemTextId;
    if (payload?.itemTextId !== currentId) return;

    this.dlService.getTerms(payload.itemTextId).subscribe((terms) => {
      this.termGroups.set(this.groupByModel(terms));
      this.state.set('done');
    });
  };

  constructor() {
    effect(() => {
      const itemTextId = this.detail().itemTextId;
      this.termGroups.set([]);
      this.state.set(itemTextId ? 'loading' : 'idle');
    });
  }

  ngOnInit(): void {
    this.sseService.addEventListener('dl-extract', this.onDone);
    this.destroyRef.onDestroy(() => {
      this.sseService.deleteEventListener('dl-extract', this.onDone);
    });
  }

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
}
