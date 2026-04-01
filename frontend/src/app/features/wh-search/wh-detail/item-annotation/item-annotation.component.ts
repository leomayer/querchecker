import {
  Component,
  NgZone,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CdkTextareaAutosize, TextFieldModule } from '@angular/cdk/text-field';
import { take } from 'rxjs/operators';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ListingService } from '../../../../core/listing.service';
import { SearchStore } from '../../search.store';
import { ExtractionStore } from '../../extraction.store';
import { ItemDetailStore } from './item-detail.store';

@Component({
  selector: 'app-item-annotation',
  providers: [ItemDetailStore],
  host: { '[class.notes-open]': 'notesOpen()' },
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TextFieldModule,
  ],
  templateUrl: './item-annotation.component.html',
  styleUrl: './item-annotation.component.scss',
})
export class ItemAnnotationComponent {
  readonly detail = input.required<WhDetailDto>();

  protected readonly store = inject(ItemDetailStore);
  private readonly searchStore = inject(SearchStore);
  private readonly listingService = inject(ListingService);
  private readonly ngZone = inject(NgZone);
  private readonly extractionStore = inject(ExtractionStore);

  @ViewChild('notesRef') notesRef!: CdkTextareaAutosize;

  readonly notesOpen = signal(false);

  protected readonly releaseYear = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    const result = this.extractionStore.lookupResults()[id];
    const year = result?.quickFacts?.['Erscheinungsjahr'];
    // Validate: must be 4-digit year (1900-2099)
    if (year && /^\d{4}$/.test(year)) {
      const numYear = parseInt(year, 10);
      if (numYear >= 1900 && numYear <= 2099) {
        return year;
      }
    }
    return null;
  });

  readonly hasNote = computed(() => !!this.store.notes().trim());
  readonly notesBtnIcon = computed(() => (this.hasNote() ? 'sticky_note_2' : 'article'));
  readonly notesBtnTooltip = computed(() =>
    this.hasNote() ? 'Notiz bearbeiten' : 'Notiz hinzufügen',
  );

  readonly saveStateIcon = computed(() => {
    if (this.store.saveState() === 'saved') return 'check_circle';
    if (this.store.hasUserEdited()) return 'edit';
    return 'check';
  });

  readonly saveStateClass = computed(() => {
    if (this.store.saveState() === 'saved') return 'saved';
    if (this.store.saveState() === 'error') return 'error';
    if (this.store.hasUserEdited()) return 'unsaved';
    return 'idle';
  });

  readonly saveStateTooltip = computed(() => {
    switch (this.store.saveState()) {
      case 'pending':
        return 'Speichert…';
      case 'saved':
        return 'Gespeichert';
      case 'error':
        return 'Fehler beim Speichern';
      default:
        return this.store.hasUserEdited() ? 'Ungespeicherte Änderungen' : 'Keine Änderungen';
    }
  });

  constructor() {
    effect(() => {
      const d = this.detail();
      untracked(() => this.store.load(d.id!, d));
      this.ngZone.onStable.pipe(take(1)).subscribe(() => this.notesRef?.resizeToFitContent(true));
    });

    effect(() => this.notesOpen.set(!!this.detail().note?.trim()));
  }

  toggleNotes(): void {
    const opening = !this.notesOpen();
    this.notesOpen.set(opening);
    if (opening) {
      this.ngZone.onStable.pipe(take(1)).subscribe(() => this.notesRef?.resizeToFitContent(true));
    }
  }

  clearNote(): void {
    this.store.updateNotes('');
  }

  onVerdict(rating: 'UP' | 'DOWN' | null): void {
    const next = this.store.verdict() === rating ? null : rating;
    this.store.setVerdict(next);
    const id = this.detail().id!;
    this.listingService.updateRating(id, next).subscribe((d) => {
      this.searchStore.applySearchPatch(id, { rating: d.rating ?? undefined });
      if (d.rating === 'DOWN' || d.rating === 'UP') {
        setTimeout(() => this.searchStore.advanceToNext(), 350);
      }
    });
  }

  onInterestClick(level: 'LOW' | 'MEDIUM' | 'HIGH'): void {
    const next = this.store.interestLevel() === level ? null : level;
    this.store.setInterestLevel(next);
    this.listingService.updateInterest(this.detail().id!, next).subscribe();
  }
}
