import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IcecatService } from './icecat.service';
import { IcecatResponse } from './model/icecat.model';

describe('IcecatService', () => {
  let service: IcecatService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IcecatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends GET to Icecat with correct params', () => {
    const mockResponse: IcecatResponse = {
      msg: 'OK',
      code: 1,
      data: {
        GeneralInfo: { IcecatId: 123, Title: 'HP Pro Mini 400 G9' },
        FeaturesGroups: [
          {
            FeatureGroup: { ID: 1, Name: { Value: 'Prozessor' } },
            Features: [
              {
                Feature: {
                  ID: 10,
                  Name: { Value: 'Prozessorfamilie' },
                  Measure: null,
                },
                Value: 'Intel Core i5',
                Presentation_Value: 'Intel Core i5',
              },
            ],
          },
        ],
      },
    };

    let result: IcecatResponse | undefined;
    service.getByGtin('0196548354795').subscribe((r) => (result = r));

    const req = httpMock.expectOne((r) => r.url.includes('/api/research/icecat/0196548354795'));
    expect(req.request.method).toBe('GET');

    req.flush(mockResponse);
    expect(result!.data?.FeaturesGroups).toHaveLength(1);
    expect(result!.data?.FeaturesGroups![0].FeatureGroup.Name.Value).toBe('Prozessor');
  });

  it('handles product-not-found response gracefully', () => {
    const notFoundResponse: IcecatResponse = { msg: 'product not found', code: -1, data: null };

    let result: IcecatResponse | undefined;
    service.getByGtin('0000000000000').subscribe((r) => (result = r));

    const req = httpMock.expectOne((r) => r.url.includes('/api/research/icecat/0000000000000'));
    req.flush(notFoundResponse);

    expect(result!.code).toBe(-1);
    expect(result!.data).toBeNull();
  });
});
