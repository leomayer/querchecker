import { Component, computed, inject, model } from '@angular/core';
import { WhLocationDto } from '../../../api/model/whLocationDto';
import { HierarchicalFilterComponent } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component';
import { FilterNode } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component.model';
import { WhMetaService } from '../../../core/wh-meta.service';

@Component({
  selector: 'app-location-filter',
  imports: [HierarchicalFilterComponent],
  templateUrl: './location-filter.component.html',
  styleUrl: './location-filter.component.scss',
})
export class LocationFilterComponent {
  locationAreaId = model<number | undefined>(undefined);

  private meta = inject(WhMetaService);

  filterNodes = computed<FilterNode[]>(() => {
    const sorted = [...(this.meta.locations() ?? [])].sort(
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
