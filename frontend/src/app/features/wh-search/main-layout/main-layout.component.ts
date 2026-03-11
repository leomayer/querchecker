import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { trigger, transition, style, animate, group, query } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { WhItemDto } from '../../../api/model/whItemDto';
import { WhSearchResultDto } from '../../../api/model/whSearchResultDto';
import { API_URLS } from '../../../core/api-urls';
import { SearchStore } from '../search.store';
import { LayoutState } from '../layout-state.enum';
import { WhFilterComponent } from '../wh-filter/wh-filter.component';
import { WhListingsComponent } from '../wh-listings/wh-listings.component';
import { RatingChangedEvent } from '../wh-listings/listing-card/listing-card.component';
import { WhSortComponent } from '../wh-sort/wh-sort.component';
import { WhDetailComponent } from '../wh-detail/wh-detail.component';
import { PlaceholderComponent } from '../../../shared/components/placeholder/placeholder.component';
import { ZoneLeftComponent } from '../../../shared/layout/zone-left/zone-left.component';
import { ZoneRightComponent } from '../../../shared/layout/zone-right/zone-right.component';

const SLIDE_IN_RIGHT = [
  style({ transform: 'translateX(40px)', opacity: 0 }),
  animate('280ms ease-out', style({ transform: 'none', opacity: 1 })),
];
const SLIDE_OUT_LEFT = [
  animate('200ms ease-in', style({ transform: 'translateX(-40px)', opacity: 0 })),
];
const FADE_IN = [
  style({ opacity: 0 }),
  animate('300ms ease-in', style({ opacity: 1 })),
];

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-main-layout',
  imports: [
    ZoneLeftComponent,
    ZoneRightComponent,
    WhFilterComponent,
    WhListingsComponent,
    WhSortComponent,
    WhDetailComponent,
    PlaceholderComponent,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
  animations: [
    trigger('rightZone', [
      // First search: slide in from right
      transition(`${LayoutState.SEARCH} => ${LayoutState.LISTINGS}`, [
        query(':enter', SLIDE_IN_RIGHT, { optional: true }),
      ]),
      // Open detail: slide in from right
      transition(`${LayoutState.LISTINGS} => ${LayoutState.DETAIL}`, [
        group([
          query(':enter', SLIDE_IN_RIGHT, { optional: true }),
          query(':leave', SLIDE_OUT_LEFT, { optional: true }),
        ]),
      ]),
      // Back to listings: slide in from left
      transition(`${LayoutState.DETAIL} => ${LayoutState.LISTINGS}`, [
        group([
          query(':enter', [
            style({ transform: 'translateX(-40px)', opacity: 0 }),
            animate('280ms ease-out', style({ transform: 'none', opacity: 1 })),
          ], { optional: true }),
          query(':leave', [
            animate('200ms ease-in', style({ transform: 'translateX(40px)', opacity: 0 })),
          ], { optional: true }),
        ]),
      ]),
      // Clear search: fade in placeholder
      transition(`* => ${LayoutState.SEARCH}`, [
        query(':enter', FADE_IN, { optional: true }),
      ]),
    ]),
    trigger('leftZone', [
      // Switch to detail: listings slide in from right in left zone
      transition(`${LayoutState.LISTINGS} => ${LayoutState.DETAIL}`, [
        query(':enter', SLIDE_IN_RIGHT, { optional: true }),
      ]),
      // Back: filter slides back in
      transition(`${LayoutState.DETAIL} => ${LayoutState.LISTINGS}`, [
        query(':enter', FADE_IN, { optional: true }),
      ]),
    ]),
  ],
})
export class MainLayoutComponent {
  protected readonly store = inject(SearchStore);
  protected readonly LayoutState = LayoutState;

  private readonly allResource = httpResource<WhItemDto[]>(
    () =>
      !this.store.searchMode()
        ? { url: API_URLS.listings, params: { ratingFilter: 'UP_NULL' } }
        : undefined,
    { defaultValue: [] },
  );

  private readonly searchResource = httpResource<WhSearchResultDto>(() => {
    const q = this.store.searchQuery();
    if (!q) return undefined;
    const params: Record<string, string | number> = { keyword: q.keyword, rows: q.rows };
    if (q.priceFrom != null) params['priceFrom'] = q.priceFrom;
    if (q.priceTo != null) params['priceTo'] = q.priceTo;
    if (q.locationAreaId != null) params['areaId'] = q.locationAreaId;
    if (q.categoryWhId != null) params['attributeTree'] = q.categoryWhId;
    if (q.paylivery) params['paylivery'] = 'true';
    return { url: API_URLS.whSearch, params };
  });

  constructor() {
    effect(() => {
      const searchMode = this.store.searchMode();
      if (searchMode) {
        this.store.setResourceState({
          listings: this.searchResource.value()?.listings ?? [],
          loading: this.searchResource.isLoading(),
          error: this.errorMessage(this.searchResource.error()),
          whTotal: this.searchResource.value()?.totalCount ?? null,
        });
      } else {
        this.store.setResourceState({
          listings: this.allResource.value() ?? [],
          loading: this.allResource.isLoading(),
          error: this.errorMessage(this.allResource.error()),
          whTotal: null,
        });
      }
    });
  }

  handleRatingChange(event: RatingChangedEvent): void {
    if (!this.store.searchMode()) {
      this.allResource.reload();
    }
    // Search-mode patches are already applied by WhListingsComponent via store.applySearchPatch
  }

  private errorMessage(e: unknown): string | null {
    if (!e) return null;
    return e instanceof Error ? e.message : 'Fehler beim Laden der Daten.';
  }
}
