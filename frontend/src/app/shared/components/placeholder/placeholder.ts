import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'app-placeholder',
  imports: [MatIconModule],
  templateUrl: './placeholder.html',
  styleUrl: './placeholder.scss',
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('400ms ease-in', style({ opacity: 1 })),
      ]),
    ]),
  ],
})
export class PlaceholderComponent {}
