import { Component, model } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { API_URLS } from '../../../core/api-urls';
import { WhLocationDto } from '../../../core/model/wh-location.model';

@Component({
  selector: 'app-location-filter',
  standalone: true,
  imports: [MatExpansionModule, MatCheckboxModule],
  templateUrl: './location-filter.component.html',
  styleUrl: './location-filter.component.scss',
})
export class LocationFilterComponent {
  locationAreaId = model<number | undefined>(undefined);

  private locationsResource = httpResource<WhLocationDto[]>(
    () => ({ url: API_URLS.whLocations }),
    { defaultValue: [] },
  );

  locations = this.locationsResource.value;
  loading = this.locationsResource.isLoading;

  select(areaId: number): void {
    this.locationAreaId.set(this.locationAreaId() === areaId ? undefined : areaId);
  }

  isSelected(areaId: number): boolean {
    return this.locationAreaId() === areaId;
  }
}
