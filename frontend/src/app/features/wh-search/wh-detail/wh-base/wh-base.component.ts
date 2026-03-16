import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DomSanitizer } from '@angular/platform-browser';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ImageGalleryComponent } from '../image-gallery/image-gallery.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-base',
  imports: [
    DatePipe,
    MatButtonModule,
    MatChipsModule,
    MatIconModule,
    MatTooltipModule,
    CustomCurrencyPipe,
    ImageGalleryComponent,
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

  openOnWillhaben(): void {
    const url = this.detail().url;
    if (url) window.open(url, '_blank', 'noopener');
  }
}
