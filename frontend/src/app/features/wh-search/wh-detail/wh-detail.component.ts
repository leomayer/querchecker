import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { WhListingDetailDto } from '../../../api/model/whListingDetailDto';
import { ListingService } from '../../../core/listing.service';
import { SearchStore } from '../search.store';
import { WhBaseComponent } from './wh-base/wh-base.component';
import { ItemAnnotationComponent } from './item-annotation/item-annotation.component';
import { ItemResearchComponent } from './item-research/item-research.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-detail',
  animations: [
    trigger('detailSlide', [
      transition(':enter', [
        style({ transform: 'translateY(100%)' }),
        animate('280ms ease-out', style({ transform: 'translateY(0)' })),
      ]),
      transition(':leave', [animate('280ms ease-in', style({ transform: 'translateY(-100%)' }))]),
    ]),
  ],
  imports: [
    MatDividerModule,
    MatIconModule,
    WhBaseComponent,
    ItemAnnotationComponent,
    ItemResearchComponent,
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

  constructor() {
    effect(() => {
      const selectedId = this.store.selectedId();
      this.detail.set(null);
      if (!selectedId) return;
      const id = +selectedId;
      this.listingService.openDetail(id).subscribe((d) => {
        this.detail.set(d);
        this.store.applySearchPatch(id, {
          viewCount: d.viewCount,
          lastViewedAt: d.lastViewedAt ?? undefined,
        });
      });
    });
  }
}
