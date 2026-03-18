export const API_URLS = {
  health: '/api/health',
  listings: '/api/listings',
  whSearch: '/api/wh/search',
  whLocations: '/api/wh/meta/locations',
  whCategories: '/api/wh/meta/categories',
  sse: (eventSourceId: string) => `/api/sse?eventSourceId=${eventSourceId}`,
  dlExtractionTerms: (whItemId: number) => `/api/dl/extraction/${whItemId}/terms`,
  dlSettings: '/api/dl/settings',
} as const;
