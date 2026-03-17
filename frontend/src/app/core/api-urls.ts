export const API_URLS = {
  listings: '/api/listings',
  whSearch: '/api/wh/search',
  whLocations: '/api/wh/meta/locations',
  whCategories: '/api/wh/meta/categories',
  sse: (eventSourceId: string) => `/api/sse?eventSourceId=${eventSourceId}`,
  dlExtractionTerms: (itemTextId: number) => `/api/dl/extraction/${itemTextId}/terms`,
  dlSettings: '/api/dl/settings',
} as const;
