import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, filter, switchMap } from 'rxjs';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { IcecatService } from '../../../../../core/icecat.service';
import { IcecatFeatureGroup } from '../../../../../core/model/icecat.model';

type SpecState = 'idle' | 'loading' | 'done' | 'not-found' | 'error';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-icecat-spec',
  imports: [MatExpansionModule, MatProgressSpinnerModule, MatIconModule],
  templateUrl: './icecat-spec.component.html',
  styleUrl: './icecat-spec.component.scss',
})
export class IcecatSpecComponent {
  readonly ean = input<string | null>(null);

  private readonly icecatService = inject(IcecatService);

  protected readonly featureGroups = signal<IcecatFeatureGroup[]>([]);
  protected readonly state = signal<SpecState>('idle');

  constructor() {
    toObservable(this.ean)
      .pipe(
        filter((ean): ean is string => !!ean),
        switchMap((ean) => {
          this.state.set('loading');
          this.featureGroups.set([]);
          return this.icecatService.getByGtin(ean).pipe(
            catchError(() => {
              this.state.set('error');
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((res) => {
        const groups = res.data?.FeaturesGroups ?? [];
        this.featureGroups.set(groups);
        this.state.set(groups.length > 0 ? 'done' : 'not-found');
      });
  }
}
