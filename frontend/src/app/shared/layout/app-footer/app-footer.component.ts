import { ChangeDetectionStrategy, Component, VERSION } from '@angular/core';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-footer',
  templateUrl: './app-footer.component.html',
  styleUrl: './app-footer.component.scss',
})
export class AppFooterComponent {
  readonly year = new Date().getFullYear();
  readonly angularVersion = VERSION.full;
}
