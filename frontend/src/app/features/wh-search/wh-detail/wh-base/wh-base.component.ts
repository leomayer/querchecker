import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { QuercheckerListingDto } from '../../../../api/model/quercheckerListingDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-base',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule, CustomCurrencyPipe],
  templateUrl: './wh-base.component.html',
  styleUrl: './wh-base.component.scss',
})
export class WhBaseComponent {
  readonly listing = input.required<QuercheckerListingDto>();

  openOnWillhaben(): void {
    const url = this.listing().url;
    if (url) window.open(url, '_blank', 'noopener');
  }
}
