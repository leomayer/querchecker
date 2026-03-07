import { ChangeDetectionStrategy, Component, inject, OnInit, signal, computed } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { QuercheckerListingDto } from '../../../api/model/quercheckerListingDto';
import { WhListingDetailDto } from '../../../api/model/whListingDetailDto';
import { CustomCurrencyPipe } from '../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ListingService } from '../../../core/listing.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-listing-detail-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTooltipModule,
    DatePipe,
    FormsModule,
    CustomCurrencyPipe,
  ],
  templateUrl: './listing-detail-dialog.component.html',
  styleUrl: './listing-detail-dialog.component.scss',
})
export class ListingDetailDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<ListingDetailDialogComponent>);
  private readonly listingService = inject(ListingService);
  readonly listing = inject<{ listing: QuercheckerListingDto }>(MAT_DIALOG_DATA).listing;

  detail = signal<WhListingDetailDto | null>(null);
  noteText = '';
  saving = signal(false);

  currentRating = computed(() => this.detail()?.rating ?? null);

  ngOnInit(): void {
    this.listingService.getDetail(this.listing.id!).subscribe((d) => {
      this.detail.set(d);
      this.noteText = d.note ?? '';
    });
  }

  openOnWillhaben(): void {
    window.open(this.listing.url, '_blank', 'noopener');
  }

  saveNote(): void {
    this.saving.set(true);
    this.listingService.updateNote(this.listing.id!, this.noteText).subscribe({
      next: () => this.dialogRef.close('saved'),
      error: () => this.saving.set(false),
    });
  }

  setRating(rating: 'UP' | 'DOWN' | null): void {
    // Toggle: gleiche Bewertung nochmal klicken → entfernen
    const next = this.currentRating() === rating ? null : rating;
    this.listingService.updateRating(this.listing.id!, next).subscribe((d) => {
      this.detail.set(d);
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}
