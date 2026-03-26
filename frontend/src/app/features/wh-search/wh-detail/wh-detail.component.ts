import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  effect,
  inject,
  signal,
} from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { MatIconModule } from '@angular/material/icon';
import { WhDetailDto } from '../../../api/model/whDetailDto';
import { ListingService } from '../../../core/listing.service';
import { SearchStore } from '../search.store';
import { WhBaseComponent } from './wh-base/wh-base.component';
import { ItemAnnotationComponent } from './item-annotation/item-annotation.component';
import { ItemResearchComponent } from './item-research/item-research.component';
import { EventSourceServerService } from '../../../shared/utils/event-source-server';
import { AppSseEventName, ListingRefreshedPayload } from '../../../core/sse-events';

const STORAGE_KEY_TOP = 'wh-detail--top-height';

@Component({
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
    MatIconModule,
    WhBaseComponent,
    ItemAnnotationComponent,
    ItemResearchComponent,
  ],
  templateUrl: './wh-detail.component.html',
  styleUrl: './wh-detail.component.scss',
})
export class WhDetailComponent implements OnDestroy {
  protected readonly store = inject(SearchStore);
  private readonly listingService = inject(ListingService);
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly sseService = inject(EventSourceServerService) as
    EventSourceServerService<AppSseEventName, ListingRefreshedPayload>;

  readonly detail = signal<WhDetailDto | null>(null);

  protected dragging: 'top' | null = null;
  private dragStartY = 0;
  private dragStartHeight = 0;

  private readonly onListingRefreshed = (payload: ListingRefreshedPayload): void => {
    const current = this.detail();
    if (!current || current.whItemId !== payload.whItemId) return;
    this.detail.set({
      ...current,
      description: payload.description ?? current.description,
      previews: payload.previews ?? current.previews,
      categoryPath: payload.categoryPath ?? current.categoryPath,
    });
  };

  constructor() {
    this.restoreHeights();
    this.sseService.addEventListener('listing-refreshed', this.onListingRefreshed);

    effect(() => {
      const selectedId = this.store.selectedId();
      this.detail.set(null);
      if (!selectedId) return;
      const id = +selectedId;
      this.listingService.openDetail(id).subscribe((detail) => {
        this.detail.set(detail);
        this.store.applySearchPatch(id, {
          viewCount: detail.viewCount,
          lastViewedAt: detail.lastViewedAt ?? undefined,
        });
      });
    });
  }

  ngOnDestroy(): void {
    this.sseService.deleteEventListener('listing-refreshed', this.onListingRefreshed);
  }

  onDividerMousedown(event: MouseEvent, pane: 'top'): void {
    this.dragging = pane;
    this.dragStartY = event.clientY;
    const prop = pane === 'top' ? '--top-height' : '--middle-height';
    const current = getComputedStyle(this.el.nativeElement).getPropertyValue(prop);
    this.dragStartHeight = parseInt(current, 10);
    event.preventDefault();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (!this.dragging) return;
    const newHeight = Math.max(100, this.dragStartHeight + (event.clientY - this.dragStartY));
    this.el.nativeElement.style.setProperty('--top-height', `${newHeight}px`);
  }

  @HostListener('document:mouseup')
  onMouseUp(): void {
    if (!this.dragging) return;
    this.saveHeights();
    this.dragging = null;
  }

  private restoreHeights(): void {
    const top = localStorage.getItem(STORAGE_KEY_TOP);
    if (top) this.el.nativeElement.style.setProperty('--top-height', `${top}px`);
  }

  private saveHeights(): void {
    const top = this.el.nativeElement.style.getPropertyValue('--top-height');
    if (top) localStorage.setItem(STORAGE_KEY_TOP, String(parseInt(top, 10)));
  }
}
