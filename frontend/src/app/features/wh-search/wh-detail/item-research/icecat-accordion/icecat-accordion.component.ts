import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IcecatFeatureGroup } from '../../../../../core/model/icecat.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-icecat-accordion',
  imports: [MatExpansionModule, MatIconModule, MatTooltipModule],
  templateUrl: './icecat-accordion.component.html',
  styleUrl: './icecat-accordion.component.scss',
})
export class IcecatAccordionComponent {
  readonly groups = input.required<IcecatFeatureGroup[]>();
  readonly preferredKeys = input<Set<string>>(new Set());
  readonly canToggle = input<boolean>(false);
  readonly toggle = output<string>();

  protected readonly preferredFeatures = computed<{ key: string; value: string }[]>(() => {
    const keys = this.preferredKeys();
    if (keys.size === 0) return [];
    const result: { key: string; value: string }[] = [];
    for (const group of this.groups()) {
      for (const feature of group.Features) {
        if (this.isPreferred(feature.Feature.Name.Value)) {
          result.push({ key: feature.Feature.Name.Value, value: feature.PresentationValue ?? '' });
        }
      }
    }
    return result;
  });

  protected isPreferred(featureName: string): boolean {
    const keys = this.preferredKeys();
    if (keys.size === 0) return false;
    const lower = featureName.toLowerCase();
    return [...keys].some((k) => lower.includes(k));
  }
}
