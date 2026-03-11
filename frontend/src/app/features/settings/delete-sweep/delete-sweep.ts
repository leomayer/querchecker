import { Component, computed, signal } from '@angular/core';
import { ThemeToggle } from '../theme-toggle/theme-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSliderModule } from '@angular/material/slider';
import { inject } from '@angular/core/primitives/di';
import { ListingService } from '../../../core/listing.service';

@Component({
  selector: 'app-delete-sweep',
  imports: [MatButtonModule, MatIconModule, MatCardModule, MatSliderModule],
  templateUrl: './delete-sweep.html',
  styleUrl: './delete-sweep.scss',
})
export class DeleteSweep {
  olderThanDays = signal(30);
  deleting = signal(false);
  deleteResult = signal<string | null>(null);
  //private readonly listingService = inject(ListingService);
  constructor(private listingService: ListingService) {}

  readonly daysLabel = computed(() => {
    const d = this.olderThanDays();
    return d === 1 ? '1 Tag' : `${d} Tage`;
  });

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
