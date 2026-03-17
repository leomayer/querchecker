import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
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

const STORAGE_KEY_TOP    = 'wh-detail--top-height';
const STORAGE_KEY_MIDDLE = 'wh-detail--middle-height';

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
  private readonly el = inject(ElementRef<HTMLElement>);

  readonly detail = signal<WhDetailDto | null>(null);

  protected dragging: 'top' | 'middle' | null = null;
  private dragStartY = 0;
  private dragStartHeight = 0;

  constructor() {
    this.restoreHeights();

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

  onDividerMousedown(event: MouseEvent, pane: 'top' | 'middle'): void {
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
    const prop = this.dragging === 'top' ? '--top-height' : '--middle-height';
    this.el.nativeElement.style.setProperty(prop, `${newHeight}px`);
  }

  @HostListener('document:mouseup')
  onMouseUp(): void {
    if (!this.dragging) return;
    this.saveHeights();
    this.dragging = null;
  }

  private restoreHeights(): void {
    const top    = localStorage.getItem(STORAGE_KEY_TOP);
    const middle = localStorage.getItem(STORAGE_KEY_MIDDLE);
    if (top)    this.el.nativeElement.style.setProperty('--top-height',    `${top}px`);
    if (middle) this.el.nativeElement.style.setProperty('--middle-height', `${middle}px`);
  }

  private saveHeights(): void {
    const style = this.el.nativeElement.style;
    const top    = style.getPropertyValue('--top-height');
    const middle = style.getPropertyValue('--middle-height');
    if (top)    localStorage.setItem(STORAGE_KEY_TOP,    String(parseInt(top, 10)));
    if (middle) localStorage.setItem(STORAGE_KEY_MIDDLE, String(parseInt(middle, 10)));
  }
}
