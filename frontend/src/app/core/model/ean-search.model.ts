export interface EanProduct {
  ean: string;
  name: string;
  categoryId: string;
  categoryName: string;
  issuingCountry: string;
}

export interface EanSearchResponse {
  page: number;
  moreproducts: boolean;
  totalproducts: number;
  productlist: EanProduct[];
}
