import { Component, input } from '@angular/core';

@Component({
  selector: 'app-zone-left',
  template: `<ng-content />`,
  styleUrl: './zone-left.component.scss',
  host: { '[style.flex-basis]': 'width()' },
})
export class ZoneLeftComponent {
  width = input('320px');
}
