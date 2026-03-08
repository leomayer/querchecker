import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WhListingDetailDto } from '../../../api/model/whListingDetailDto';
import { CustomCurrencyPipe } from '../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ListingService } from '../../../core/listing.service';
import { SearchStore } from '../search.store';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-detail',
  animations: [
    trigger('detailSlide', [
      transition(':enter', [
        style({ transform: 'translateY(100%)' }),
        animate('280ms ease-out', style({ transform: 'translateY(0)' })),
      ]),
      transition(':leave', [
        animate('280ms ease-in', style({ transform: 'translateY(-100%)' })),
      ]),
    ]),
  ],
  imports: [
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTooltipModule,
    DatePipe,
    FormsModule,
    CustomCurrencyPipe,
  ],
  templateUrl: './wh-detail.component.html',
  styleUrl: './wh-detail.component.scss',
})
export class WhDetailComponent {
  protected readonly store = inject(SearchStore);
  private readonly listingService = inject(ListingService);

  readonly listing = computed(() => {
    const id = this.store.selectedId();
    if (!id) return null;
    return this.store.patchedListings().find((l) => l.id?.toString() === id) ?? null;
  });

  readonly currentListingArr = computed(() => {
    const lst = this.listing();
    return lst ? [lst] : [];
  });

  detail = signal<WhListingDetailDto | null>(null);
  noteText = '';
  saving = signal(false);

  readonly currentRating = computed(() => this.detail()?.rating ?? null);

  constructor() {
    effect(() => {
      const listing = this.listing();
      this.detail.set(null);
      this.noteText = '';
      if (!listing?.id) return;
      this.listingService.getDetail(listing.id).subscribe((d) => {
        this.detail.set(d);
        this.noteText = d.note ?? '';
      });
    });
  }

  openOnWillhaben(): void {
    const url = this.listing()?.url;
    if (url) window.open(url, '_blank', 'noopener');
  }

  saveNote(): void {
    const id = this.listing()?.id;
    if (!id) return;
    this.saving.set(true);
    this.listingService.updateNote(id, this.noteText).subscribe({
      next: () => {
        this.saving.set(false);
        this.store.applySearchPatch(id, { hasNote: !!(this.noteText?.trim()) });
      },
      error: () => this.saving.set(false),
    });
  }

  setRating(rating: 'UP' | 'DOWN' | null): void {
    const id = this.listing()?.id;
    if (!id) return;
    const next = this.currentRating() === rating ? null : rating;
    this.listingService.updateRating(id, next).subscribe((d) => {
      this.detail.set(d);
      this.store.applySearchPatch(id, { rating: d.rating ?? undefined });
      if (d.rating === 'DOWN' || d.rating === 'UP') {
        setTimeout(() => this.store.advanceToNext(), 350);
      }
    });
  }
}
