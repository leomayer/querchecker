import { Component, VERSION } from '@angular/core';

@Component({
  selector: 'app-footer',
  templateUrl: './app-footer.html',
  styleUrl: './app-footer.scss',
})
export class AppFooterComponent {
  readonly year = new Date().getFullYear();
  readonly angularVersion = VERSION.full;
}
