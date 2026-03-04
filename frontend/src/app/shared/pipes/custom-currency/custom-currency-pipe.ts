import { Pipe, PipeTransform, Inject, LOCALE_ID } from '@angular/core';
import { CurrencyPipe, formatNumber } from '@angular/common';

@Pipe({
  name: 'customCurrency',
  standalone: true,
})
export class CustomCurrencyPipe implements PipeTransform {
  private currencyPipe: CurrencyPipe;

  constructor(@Inject(LOCALE_ID) private locale: string) {
    // Manually create the instance
    this.currencyPipe = new CurrencyPipe(this.locale);
  }
  transform(value: number | string | null | undefined, currencyCode: string = 'EUR'): string {
    if (value == null || value === '') return '';

    const numericValue = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(numericValue)) return '';

    // 1. Get the standard formatted string from Angular's own pipe
    // This handles the symbol (e.g., €) and the thousands separators automatically
    const standardFormat = this.currencyPipe.transform(
      numericValue,
      currencyCode,
      'symbol',
      '1.2-2',
      this.locale,
    );

    if (!standardFormat) return '';

    // 2. Check if it's a whole number
    if (numericValue % 1 === 0) {
      // Find the decimal separator for the current locale
      const decimalSeparator = formatNumber(1.1, this.locale).substring(1, 2);

      // Split by the decimal separator and replace '00' with the Figure Dash (\u2012)
      const parts = standardFormat.split(decimalSeparator);
      return `${parts[0]}${decimalSeparator}\u2012`;
    }

    // Return the standard format for decimals (e.g., € 1.600,50)
    return standardFormat;
  }
}
