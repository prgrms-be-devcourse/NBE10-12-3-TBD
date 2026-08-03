declare global {
  interface KakaoMap {
    setCenter: (center: unknown) => void;
    panTo: (center: unknown) => void;
    setBounds: (bounds: unknown) => void;
    setLevel: (level: number) => void;
  }

  interface KakaoMarker {
    setMap: (map: unknown | null) => void;
  }

  interface KakaoLatLngBounds {
    extend: (position: unknown) => void;
  }

  interface KakaoCustomOverlay {
    setMap: (map: unknown | null) => void;
    setPosition: (position: unknown) => void;
    setContent: (content: string | HTMLElement) => void;
  }

  interface KakaoPlaceItem {
    id: string;
    place_name: string;
    category_name: string;
    address_name: string;
    road_address_name: string;
    phone: string;
    y: string;
    x: string;
  }

  interface KakaoPagination {
    current: number;
    last: number;
    perPage: number;
    totalCount: number;
    hasNextPage: boolean;
    hasPrevPage: boolean;
    nextPage: () => void;
    prevPage: () => void;
    gotoPage: (page: number) => void;
  }

  interface KakaoPlaces {
    keywordSearch: (
      query: string,
      callback: (
        data: KakaoPlaceItem[],
        status: string,
        pagination: KakaoPagination,
      ) => void,
      options?: object,
    ) => void;

    categorySearch: (
      categoryCode: string,
      callback: (
        data: KakaoPlaceItem[],
        status: string,
        pagination: KakaoPagination,
      ) => void,
      options?: object,
    ) => void;
  }

  interface KakaoStatus {
    OK: string;
    ZERO_RESULT: string;
    ERROR: string;
  }

  interface Window {
    kakao?: {
      maps?: {
        load: (callback: () => void) => void;

        Map: new (container: HTMLElement, options: object) => KakaoMap;

        LatLng: new (lat: number, lng: number) => unknown;

        LatLngBounds: new () => KakaoLatLngBounds;

        Marker: new (options: {
          position: unknown;
          map?: unknown;
        }) => KakaoMarker;

        CustomOverlay: new (options: {
          map?: unknown;
          position: unknown;
          content: string | HTMLElement;
          xAnchor?: number;
          yAnchor?: number;
          zIndex?: number;
          clickable?: boolean;
        }) => KakaoCustomOverlay;

        services?: {
          Places: new (map?: unknown) => KakaoPlaces;
          Status: KakaoStatus;
        };
      };
    };
  }
}

export {};
