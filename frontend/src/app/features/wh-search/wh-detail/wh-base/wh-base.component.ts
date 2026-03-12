import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { WhItemDto } from '../../../../api/model/whItemDto';
import { WhPreviewDto } from '../../../../api/model/whPreviewDto';
import { WhListingDetailDto } from '../../../../api/model/whListingDetailDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ImageGalleryComponent } from '../image-gallery/image-gallery.component';

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
  ],
  templateUrl: './wh-base.component.html',
  styleUrl: './wh-base.component.scss',
})
export class WhBaseComponent {
  readonly listing = input.required<WhItemDto>();
  readonly previews = input<WhPreviewDto[]>([]);
  readonly detail = input<WhListingDetailDto | null>(null);

  openOnWillhaben(): void {
    const url = this.listing().url;
    if (url) window.open(url, '_blank', 'noopener');
  }
}
