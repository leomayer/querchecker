import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-zone-right',
  template: `<ng-content />`,
  styleUrl: './zone-right.component.scss',
})
export class ZoneRightComponent {}
