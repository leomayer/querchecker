import {
  Component,
  inject,
  signal,
  computed,
  ElementRef,
  viewChild,
  AfterViewInit,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { GalleryImage } from '../../../features/wh-search/wh-detail/image-gallery/image-gallery.model';

export interface LightboxData {
  images: GalleryImage[];
  startIndex: number;
}

@Component({
  selector: 'app-image-lightbox',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  templateUrl: './image-lightbox.component.html',
  styleUrl: './image-lightbox.component.scss',
})
export class ImageLightboxComponent implements AfterViewInit {
  private data: LightboxData = inject(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ImageLightboxComponent>);

  imgEl = viewChild<ElementRef<HTMLImageElement>>('imgEl');

  images = this.data.images;
  activeIndex = signal(this.data.startIndex);
  activeImage = computed(() => this.images[this.activeIndex()] ?? null);
  total = computed(() => this.images.length);

  // Zoom state
  scale = signal(1);
  translateX = signal(0);
  translateY = signal(0);
  private isDragging = false;
  private dragStartX = 0;
  private dragStartY = 0;
  private dragOriginX = 0;
  private dragOriginY = 0;

  transform = computed(
    () => `translate(${this.translateX()}px, ${this.translateY()}px) scale(${this.scale()})`,
  );

  ngAfterViewInit(): void {
    // keyboard navigation
    document.addEventListener('keydown', this.onKey);
  }

  private onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowLeft') this.prev();
    else if (e.key === 'ArrowRight') this.next();
    else if (e.key === 'Escape') this.close();
  };

  close(): void {
    document.removeEventListener('keydown', this.onKey);
    this.dialogRef.close();
  }

  prev(): void {
    this.activeIndex.update((i) => (i - 1 + this.total()) % this.total());
    this.resetZoom();
  }

  next(): void {
    this.activeIndex.update((i) => (i + 1) % this.total());
    this.resetZoom();
  }

  select(index: number): void {
    this.activeIndex.set(index);
    this.resetZoom();
  }

  // ── Zoom via scroll wheel ──────────────────────────────────────────────────

  onWheel(e: WheelEvent): void {
    e.preventDefault();
    const delta = e.deltaY > 0 ? -0.15 : 0.15;
    this.scale.update((s) => Math.min(5, Math.max(1, s + delta)));
    if (this.scale() === 1) this.resetZoom();
  }

  onThumbWheel(e: WheelEvent): void {
    if (this.total() <= 1) return;
    e.preventDefault();
    e.deltaY > 0 ? this.next() : this.prev();
  }

  // ── Drag to pan ───────────────────────────────────────────────────────────

  onMouseDown(e: MouseEvent): void {
    if (this.scale() <= 1) return;
    this.isDragging = true;
    this.dragStartX = e.clientX;
    this.dragStartY = e.clientY;
    this.dragOriginX = this.translateX();
    this.dragOriginY = this.translateY();
    e.preventDefault();
  }

  onMouseMove(e: MouseEvent): void {
    if (!this.isDragging) return;
    this.translateX.set(this.dragOriginX + (e.clientX - this.dragStartX));
    this.translateY.set(this.dragOriginY + (e.clientY - this.dragStartY));
  }

  onMouseUp(): void {
    this.isDragging = false;
  }

  private resetZoom(): void {
    this.scale.set(1);
    this.translateX.set(0);
    this.translateY.set(0);
  }
}
