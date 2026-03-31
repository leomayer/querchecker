import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatDividerModule } from '@angular/material/divider';
import { ListingService } from '../../../core/listing.service';
import { clearSearchHistory, loadSearchHistory } from '../../../core/search-history';

@Component({
  selector: 'app-delete-sweep',
  imports: [MatButtonModule, MatIconModule, MatSliderModule, MatDividerModule],
  templateUrl: './delete-sweep.html',
  styleUrl: './delete-sweep.scss',
})
export class DeleteSweep {
  private readonly listingService = inject(ListingService);

  olderThanDays = signal(30);
  deleting = signal(false);
  deleteResult = signal<string | null>(null);

  historyCount = signal(loadSearchHistory().length);
  historyCleared = signal(false);

  readonly daysLabel = computed(() => {
    const d = this.olderThanDays();
    return d === 1 ? '1 Tag' : `${d} Tage`;
  });

  clearHistory(): void {
    clearSearchHistory();
    this.historyCount.set(0);
    this.historyCleared.set(true);
  }

  cleanupDownRated(): void {
    this.deleting.set(true);
    this.deleteResult.set(null);
    this.listingService.cleanupByRating('DOWN', this.olderThanDays()).subscribe({
      next: (result) => {
        this.deleteResult.set(`${result.deleted} Inserate gelöscht.`);
        this.deleting.set(false);
      },
      error: () => {
        this.deleteResult.set('Fehler beim Löschen.');
        this.deleting.set(false);
      },
    });
  }
}
