import { Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { API_URLS } from './api-urls';
import { WhCategoryDto } from '../api/model/whCategoryDto';
import { WhLocationDto } from '../api/model/whLocationDto';

/**
 * Root-level service that loads WH meta data (categories + locations) once
 * and keeps them cached for the app's lifetime.
 *
 * Both CategoryFilterComponent and LocationFilterComponent are recreated on every
 * route change (MainLayoutComponent is registered under three separate paths).
 * Without this service, each recreation triggers a fresh HTTP request, causing
 * a flash of empty chip state until the response arrives.
 */
@Injectable({ providedIn: 'root' })
export class WhMetaService {
  private categoriesResource = httpResource<WhCategoryDto[]>(
    () => ({ url: API_URLS.whCategories }),
    { defaultValue: [] },
  );

  private locationsResource = httpResource<WhLocationDto[]>(() => ({ url: API_URLS.whLocations }), {
    defaultValue: [],
  });

  readonly categories = this.categoriesResource.value;
  readonly locations = this.locationsResource.value;
}
