import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { API_URLS } from '../../../core/api-urls';
import { WhLocationDto } from '../../../api/model/whLocationDto';
import { HierarchicalFilterComponent } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component';
import { FilterNode } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-location-filter',
  imports: [HierarchicalFilterComponent],
  templateUrl: './location-filter.component.html',
  styleUrl: './location-filter.component.scss',
})
export class LocationFilterComponent {
  locationAreaId = model<number | undefined>(undefined);

  private locationsResource = httpResource<WhLocationDto[]>(() => ({ url: API_URLS.whLocations }), {
    defaultValue: [],
  });

  filterNodes = computed<FilterNode[]>(() => {
    const sorted = [...(this.locationsResource.value() ?? [])].sort(
      (a, b) => (a.areaId ?? 0) - (b.areaId ?? 0),
    );
    return this.toFilterNodes(sorted);
  });

  private toFilterNodes(dtos: WhLocationDto[], parentName?: string): FilterNode[] {
    return dtos.map((dto) => ({
      id: String(dto.areaId ?? ''),
      name: dto.name ?? '',
      level: dto.level ?? 0,
      parentName,
      children: dto.children?.length ? this.toFilterNodes(dto.children, dto.name) : undefined,
    }));
  }

  onSelectionChange(node: FilterNode | null): void {
    this.locationAreaId.set(node ? parseInt(node.id, 10) : undefined);
  }
}
