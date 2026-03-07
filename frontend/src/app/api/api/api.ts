export * from './listingDetail.service';
import { ListingDetailService } from './listingDetail.service';
export * from './listings.service';
import { ListingsService } from './listings.service';
export * from './willhabenMeta.service';
import { WillhabenMetaService } from './willhabenMeta.service';
export * from './willhabenSearch.service';
import { WillhabenSearchService } from './willhabenSearch.service';
export const APIS = [ListingDetailService, ListingsService, WillhabenMetaService, WillhabenSearchService];
