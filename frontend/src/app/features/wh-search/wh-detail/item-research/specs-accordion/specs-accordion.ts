import { Component, computed, input, output } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SpecsFeatureGroup } from '../../../../../core/model/lookup.model';

/**
 * Generisches Spezifikations-Accordion für HTML-Fetch-Quellen (GSMArena, FlatpanelsHD).
 * Eingabe: einfaches SpecsFeatureGroup[]-Format (name + features[{name, value}]).
 */
@Component({
  selector: 'app-specs-accordion',
  imports: [MatExpansionModule, MatIconModule, MatTooltipModule],
  templateUrl: './specs-accordion.html',
  styleUrl: './specs-accordion.scss',
})
export class SpecsAccordionComponent {
  readonly groups = input.required<SpecsFeatureGroup[]>();
  readonly preferredKeys = input<Set<string>>(new Set());
  readonly canToggle = input<boolean>(false);
  readonly toggle = output<string>();

  protected readonly preferredFeatures = computed<{ key: string; value: string }[]>(() => {
    const keys = this.preferredKeys();
    if (keys.size === 0) return [];
    const result: { key: string; value: string }[] = [];
    for (const group of this.groups()) {
      for (const feature of group.features) {
        if (this.isPreferred(feature.name)) {
          result.push({ key: feature.name, value: feature.value });
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
