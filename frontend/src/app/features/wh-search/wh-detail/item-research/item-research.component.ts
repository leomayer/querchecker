import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-item-research',
  template: `<p class="placeholder">Weiterführende Recherche – kommt bald.</p>`,
  styles: [`.placeholder { margin: 0; color: var(--mat-sys-on-surface-variant); font-style: italic; }`],
})
export class ItemResearchComponent {}
