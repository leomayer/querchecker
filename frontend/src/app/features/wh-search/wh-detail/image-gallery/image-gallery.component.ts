import { Component, input, signal, computed, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { GalleryImage } from './image-gallery.model';
import {
  ImageLightboxComponent,
  LightboxData,
} from '../../../../shared/components/image-lightbox/image-lightbox.component';

@Component({
  selector: 'app-image-gallery',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  templateUrl: './image-gallery.component.html',
  styleUrl: './image-gallery.component.scss',
})
export class ImageGalleryComponent {
  images = input.required<GalleryImage[]>();

  private dialog = inject(MatDialog);

  activeIndex = signal(0);

  activeImage = computed(() => this.images()[this.activeIndex()] ?? null);
  total = computed(() => this.images().length);

  prev(): void {
    this.activeIndex.update((i) => (i - 1 + this.total()) % this.total());
  }

  next(): void {
    this.activeIndex.update((i) => (i + 1) % this.total());
  }

  select(index: number): void {
    this.activeIndex.set(index);
  }

  onWheel(e: WheelEvent): void {
    if (this.total() <= 1) return;
    e.preventDefault();
    e.deltaY > 0 ? this.next() : this.prev();
  }

  openLightbox(): void {
    const data: LightboxData = {
      images: this.images(),
      startIndex: this.activeIndex(),
    };
    this.dialog.open(ImageLightboxComponent, {
      data,
      maxWidth: '100vw',
      maxHeight: '100vh',
      width: '100vw',
      height: '100vh',
      panelClass: 'lightbox-dialog',
    });
  }
}
