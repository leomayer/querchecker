import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { EanSearchService } from '../../../../../core/ean-search.service';
import { EanProduct } from '../../../../../core/model/ean-search.model';

type SearchState = 'idle' | 'loading' | 'done' | 'empty' | 'error';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-upc-search',
  imports: [MatProgressSpinnerModule, MatIconModule],
  templateUrl: './upc-search.component.html',
  styleUrl: './upc-search.component.scss',
})
export class UpcSearchComponent {
  readonly query = input('');
  readonly productSelected = output<EanProduct>();

  private readonly eanSearchService = inject(EanSearchService);

  protected readonly results = signal<EanProduct[]>([]);
  protected readonly selected = signal<EanProduct | null>(null);
  protected readonly state = signal<SearchState>('idle');

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap((q) => {
          if (!q) {
            this.state.set('idle');
            this.results.set([]);
            this.selected.set(null);
            return EMPTY;
          }
          this.state.set('loading');
          this.results.set([]);
          this.selected.set(null);
          return this.eanSearchService.search(q).pipe(
            catchError(() => {
              this.state.set('error');
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((res) => {
        const items = (res.productlist ?? []).filter((p) => !!p.ean);
        this.results.set(items);
        this.state.set(items.length > 0 ? 'done' : 'empty');
      });
  }

  protected select(product: EanProduct): void {
    this.selected.set(product);
    this.productSelected.emit(product);
  }
}
