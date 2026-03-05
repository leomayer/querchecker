import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-zone-left',
  template: `<ng-content />`,
  styleUrl: './zone-left.component.scss',
})
export class ZoneLeftComponent {}
