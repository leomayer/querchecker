import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DomSanitizer } from '@angular/platform-browser';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ImageGalleryComponent } from '../image-gallery/image-gallery.component';
import { HierarchicalFilterComponent } from '../../../../shared/components/hierarchical-filter-component/hierarchical-filter-component';
import { FilterNode } from '../../../../shared/components/hierarchical-filter-component/hierarchical-filter-component.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-base',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    CustomCurrencyPipe,
    ImageGalleryComponent,
    HierarchicalFilterComponent,
  ],
  templateUrl: './wh-base.component.html',
  styleUrl: './wh-base.component.scss',
})
export class WhBaseComponent {
  readonly detail = input.required<WhDetailDto>();

  private readonly sanitizer = inject(DomSanitizer);

  readonly descriptionHtml = computed(() =>
    this.sanitizer.bypassSecurityTrustHtml(this.detail().description ?? ''),
  );

  readonly categoryChips = computed<FilterNode[]>(() =>
    (this.detail().categoryPath ?? []).map((cat, i) => ({
      id: String(cat.whId ?? i),
      name: cat.name ?? '',
      level: cat.level ?? i,
    })),
  );

  openOnWillhaben(): void {
    const url = this.detail().url;
    if (url) window.open(url, '_blank', 'noopener');
  }
}
