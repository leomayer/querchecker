import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-gradient-progress-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './gradient-progress-bar.component.html',
  styleUrl: './gradient-progress-bar.component.scss',
})
export class GradientProgressBarComponent {
  /** Current usage value (raw number, same unit as limit). */
  readonly usage = input.required<number>();
  /** Maximum/limit value. 0 = no limit → bar not shown. */
  readonly limit = input.required<number>();

  protected readonly pct = computed(() => {
    const l = this.limit();
    if (l <= 0) return 0;
    return Math.min(100, Math.max(0, (this.usage() / l) * 100));
  });

  protected readonly fillColor = computed(() => {
    // green (hue 120) → yellow (hue 60) → red (hue 0)
    const hue = Math.round(120 - this.pct() * 1.2);
    return `hsl(${hue}, 62%, 40%)`;
  });

  protected readonly label = computed(() => {
    const fmt = (n: number) => n.toLocaleString('de-AT');
    return `${fmt(this.usage())} / ${fmt(this.limit())}`;
  });
}
