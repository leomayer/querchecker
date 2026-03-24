/** Simples Feature-Gruppen-Format für HTML-Fetch-Quellen (GSMArena, FlatpanelsHD). */
export interface SpecsFeatureGroup {
  name: string;
  features: { name: string; value: string }[];
}

export interface IcecatFeature {
  Feature: {
    ID: number;
    Name: { Value: string };
    Measure: { Signs: { _: string } } | null;
  };
  Value: string;
  PresentationValue: string;
}

export interface IcecatFeatureGroup {
  FeatureGroup: {
    ID: number;
    Name: { Value: string };
  };
  Features: IcecatFeature[];
}

export interface IcecatData {
  GeneralInfo?: {
    IcecatId: number;
    Title: string;
    Brand?: string;
    ReleaseDate?: string;
    EndOfLifeDate?: string;
  };
  CatalogObjectCloud?: {
    ProductPage?: { URL?: string };
  };
  FeaturesGroups?: IcecatFeatureGroup[];
}

export interface IcecatResponse {
  msg: string;
  code: number;
  data: IcecatData | null;
}
