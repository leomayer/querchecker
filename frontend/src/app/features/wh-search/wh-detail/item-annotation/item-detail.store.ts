import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { EMPTY, Subject } from 'rxjs';
import {
  catchError,
  debounceTime,
  delay,
  distinctUntilChanged,
  switchMap,
  takeUntil,
  tap,
} from 'rxjs/operators';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ListingService } from '../../../../core/listing.service';

interface ItemDetailState {
  itemId: number | null;
  verdict: 'UP' | 'DOWN' | null;
  interestLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  tags: string[];
  notes: string;
  saveState: 'idle' | 'pending' | 'saved' | 'error';
  hasUserEdited: boolean;
}

const initialState: ItemDetailState = {
  itemId: null,
  verdict: null,
  interestLevel: null,
  tags: [],
  notes: '',
  saveState: 'idle',
  hasUserEdited: false,
};

export const ItemDetailStore = signalStore(
  withState<ItemDetailState>(initialState),
  withMethods((store) => {
    const listingService = inject(ListingService);
    const notes$ = new Subject<string>();
    const destroy$ = new Subject<void>();

    return {
      _startPipeline(): void {
        notes$
          .pipe(
            debounceTime(900),
            distinctUntilChanged(),
            tap(() => patchState(store, { saveState: 'pending' })),
            switchMap((text) =>
              listingService.updateNote(store.itemId()!, text).pipe(
                tap(() => patchState(store, { saveState: 'saved' })),
                delay(2000),
                tap(() => patchState(store, { saveState: 'idle' })),
                catchError(() => {
                  patchState(store, { saveState: 'error' });
                  return EMPTY;
                }),
              ),
            ),
            takeUntil(destroy$),
          )
          .subscribe();
      },

      _flush(): void {
        if (store.hasUserEdited() && store.saveState() !== 'saved') {
          const id = store.itemId();
          if (id !== null) listingService.updateNote(id, store.notes()).subscribe();
        }
      },

      _teardown(): void {
        destroy$.next();
        destroy$.complete();
      },

      load(id: number, detail: WhDetailDto | null): void {
        patchState(store, {
          itemId: id,
          verdict: (detail?.rating as 'UP' | 'DOWN' | null) ?? null,
          interestLevel: (detail?.interestLevel as 'LOW' | 'MEDIUM' | 'HIGH' | null) ?? null,
          tags: detail?.tags ?? [],
          notes: detail?.note ?? '',
          saveState: 'idle',
          hasUserEdited: false,
        });
      },

      updateNotes(text: string): void {
        patchState(store, { notes: text, hasUserEdited: true });
        notes$.next(text);
      },

      setVerdict(verdict: 'UP' | 'DOWN' | null): void {
        patchState(store, { verdict });
      },

      setInterestLevel(level: 'LOW' | 'MEDIUM' | 'HIGH' | null): void {
        patchState(store, { interestLevel: level });
      },

      setTags(tags: string[]): void {
        patchState(store, { tags });
      },
    };
  }),
  withHooks({
    onInit(store) {
      store._startPipeline();
    },
    onDestroy(store) {
      store._flush();
      store._teardown();
    },
  }),
);
