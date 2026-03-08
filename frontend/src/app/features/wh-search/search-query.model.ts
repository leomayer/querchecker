export interface SearchQuery {
  keyword: string;
  rows: number;
  priceFrom?: number;
  priceTo?: number;
  locationAreaId?: number;
  categoryWhId?: number;
  paylivery?: boolean;
}
