import { Component, computed, inject, model } from '@angular/core';
import { WhCategoryDto } from '../../../api/model/whCategoryDto';
import { HierarchicalFilterComponent } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component';
import { FilterNode } from '../../../shared/components/hierarchical-filter-component/hierarchical-filter-component.model';
import { WhMetaService } from '../../../core/wh-meta.service';

@Component({
  selector: 'app-category-filter',
  imports: [HierarchicalFilterComponent],
  templateUrl: './category-filter.component.html',
  styleUrl: './category-filter.component.scss',
})
export class CategoryFilterComponent {
  categoryWhId = model<number | undefined>(undefined);

  private meta = inject(WhMetaService);

  filterNodes = computed<FilterNode[]>(() => {
    const sorted = [...(this.meta.categories() ?? [])].sort((a, b) =>
      (a.name ?? '').localeCompare(b.name ?? ''),
    );
    return this.toFilterNodes(sorted);
  });

  private toFilterNodes(dtos: WhCategoryDto[], parentName?: string): FilterNode[] {
    return dtos.map((dto) => ({
      id: String(dto.whId ?? ''),
      name: dto.name ?? '',
      level: dto.level ?? 0,
      parentName,
      children: dto.children?.length ? this.toFilterNodes(dto.children, dto.name) : undefined,
    }));
  }

  onSelectionChange(node: FilterNode | null): void {
    this.categoryWhId.set(node ? parseInt(node.id, 10) : undefined);
  }
}
