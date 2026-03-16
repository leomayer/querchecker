import {
  ChangeDetectionStrategy,
  Component,
  NgZone,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CdkTextareaAutosize, TextFieldModule } from '@angular/cdk/text-field';
import { take } from 'rxjs/operators';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ListingService } from '../../../../core/listing.service';
import { SearchStore } from '../../search.store';
import { ItemDetailStore } from './item-detail.store';

interface PredefinedTag {
  label: string;
  positive: boolean;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-item-annotation',
  providers: [ItemDetailStore],
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TextFieldModule,
  ],
  templateUrl: './item-annotation.component.html',
  styleUrl: './item-annotation.component.scss',
})
export class ItemAnnotationComponent {
  readonly detail = input.required<WhDetailDto>();

  protected readonly store = inject(ItemDetailStore);
  private readonly searchStore = inject(SearchStore);
  private readonly listingService = inject(ListingService);
  private readonly ngZone = inject(NgZone);

  @ViewChild('notesRef') notesRef!: CdkTextareaAutosize;

  readonly PREDEFINED_TAGS: PredefinedTag[] = [
    { label: 'Guter Preis', positive: true },
    { label: 'Überlegenswert', positive: true },
    { label: 'Zu teuer', positive: false },
    { label: 'Schlechte Fotos', positive: false },
  ];

  readonly customTags = computed(() =>
    this.store.tags().filter((t) => !this.PREDEFINED_TAGS.some((p) => p.label === t)),
  );

  customTagInput = '';

  constructor() {
    effect(() => {
      const d = this.detail();
      untracked(() => this.store.load(d.id!, d));
      this.ngZone.onStable.pipe(take(1)).subscribe(() => this.notesRef?.resizeToFitContent(true));
    });
  }

  isTagSelected(label: string): boolean {
    return this.store.tags().includes(label);
  }

  onVerdict(rating: 'UP' | 'DOWN' | null): void {
    const next = this.store.verdict() === rating ? null : rating;
    this.store.setVerdict(next);
    const id = this.detail().id!;
    this.listingService.updateRating(id, next).subscribe((d) => {
      this.searchStore.applySearchPatch(id, { rating: d.rating ?? undefined });
      if (d.rating === 'DOWN' || d.rating === 'UP') {
        setTimeout(() => this.searchStore.advanceToNext(), 350);
      }
    });
  }

  onInterestClick(level: 'LOW' | 'MEDIUM' | 'HIGH'): void {
    const next = this.store.interestLevel() === level ? null : level;
    this.store.setInterestLevel(next);
    this.listingService.updateInterest(this.detail().id!, next).subscribe();
  }

  onToggleTag(label: string): void {
    const current = this.store.tags();
    const next = current.includes(label) ? current.filter((t) => t !== label) : [...current, label];
    this.store.setTags(next);
    this.listingService.updateTags(this.detail().id!, next).subscribe();
  }

  onAddCustomTag(): void {
    const tag = this.customTagInput.trim();
    if (!tag || this.store.tags().includes(tag)) return;
    const next = [...this.store.tags(), tag];
    this.store.setTags(next);
    this.customTagInput = '';
    this.listingService.updateTags(this.detail().id!, next).subscribe();
  }
}
