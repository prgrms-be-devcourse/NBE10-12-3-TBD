"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { MapPin, Navigation, Search, X } from "lucide-react";
import Link from "next/link";
import AppShell, { SidebarCard, SidebarProfile } from "@/components/AppShell";
import { apiFetchJson } from "@/lib/api";

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

interface KakaoOverlay {
  setMap: (map: unknown | null) => void;
  setPosition: (position: unknown) => void;
}

interface KakaoMap {
  setCenter: (center: unknown) => void;
  setBounds: (bounds: unknown) => void;
  setLevel: (level: number) => void;
}

interface KakaoLatLngBounds {
  extend: (position: unknown) => void;
}

/**
 * 카카오 장소 검색 페이지네이션 (무한 스크롤 페이지네이션 기능)
 */
interface KakaoPagination {
  hasNextPage: boolean;
  nextPage: () => void;
}

interface HotPlace {
  id: number;
  name: string;
  category: string;
  region2: string;
}

interface KakaoRestaurant {
  kakaoPlaceId: string;
  name: string;
  category: string;
  address: string;
  roadAddress: string;
  region1: string;
  region2: string;
  region3: string;
  phone: string;
  lat: number;
  lng: number;
}

const categories = ["전체", "한식", "일식", "양식", "중식", "분식", "카페"];

const categoryLabelMap: Record<string, string> = {
  KOREAN: "한식",
  JAPANESE: "일식",
  WESTERN: "양식",
  CHINESE: "중식",
  SNACK: "분식",
  CAFE: "카페",
  ASIAN: "아시안",
  ETC: "기타",
};

/**
 * 카카오 키워드 검색 결과 중
 * 음식점·카페 계열만 남김
 */
function isFoodOrCafe(item: KakaoPlaceItem): boolean {
  return (
    item.category_name.startsWith("음식점") ||
    item.category_name.startsWith("카페")
  );
}

/**
 * 카카오 API 결과를
 * 화면에서 사용하는 데이터로 변환
 */
function mapKakaoPlaces(data: KakaoPlaceItem[]): KakaoRestaurant[] {
  return data.map((item) => {
    const addressParts = item.address_name ? item.address_name.split(" ") : [];

    return {
      kakaoPlaceId: item.id,
      name: item.place_name,
      category: item.category_name,
      address: item.address_name,
      roadAddress: item.road_address_name,
      region1: addressParts[0] || "",
      region2: addressParts[1] || "",
      region3: addressParts[2] || "",
      phone: item.phone,
      lat: parseFloat(item.y),
      lng: parseFloat(item.x),
    };
  });
}

/**
 * 특정 검색 의도에서 관련 없는 업종을 제거
 *
 * 일반 키워드는 카카오 검색 결과를 그대로 유지하고,
 * 디저트 계열 검색일 때만 카페·베이커리 계열로 제한
 */
function filterPlacesByKeyword(
  places: KakaoPlaceItem[],
  keyword: string,
): KakaoPlaceItem[] {
  const normalizedKeyword = keyword.trim().toLowerCase();

  const dessertKeywords = [
    "디저트",
    "케이크",
    "베이커리",
    "빵",
    "마카롱",
    "도넛",
  ];

  const isDessertSearch = dessertKeywords.some((dessertKeyword) =>
    normalizedKeyword.includes(dessertKeyword),
  );

  if (!isDessertSearch) {
    return places;
  }

  return places.filter((place) => {
    const placeText =
      `${place.place_name} ${place.category_name}`.toLowerCase();

    return (
      place.category_name.startsWith("카페") ||
      placeText.includes("베이커리") ||
      placeText.includes("디저트") ||
      placeText.includes("케이크") ||
      placeText.includes("제과") ||
      placeText.includes("도넛") ||
      placeText.includes("마카롱")
    );
  });
}

/**
 * 음식점 결과 + 카페 결과 합치기
 *
 * 카카오 장소 ID 기준 중복 제거
 */
function mergePlaces(...placeGroups: KakaoPlaceItem[][]): KakaoRestaurant[] {
  const placeMap = new Map<string, KakaoPlaceItem>();

  placeGroups.flat().forEach((place) => {
    placeMap.set(place.id, place);
  });

  return mapKakaoPlaces(Array.from(placeMap.values()));
}

/**
 * 기존 검색 결과 뒤에 새 결과 추가
 *
 * 카카오 장소 ID를 기준으로 중복 제거
 */
function appendUniquePlaces(
  previous: KakaoRestaurant[],
  next: KakaoRestaurant[],
): KakaoRestaurant[] {
  const placeMap = new Map<string, KakaoRestaurant>();

  previous.forEach((place) => {
    placeMap.set(place.kakaoPlaceId, place);
  });

  next.forEach((place) => {
    placeMap.set(place.kakaoPlaceId, place);
  });

  return Array.from(placeMap.values());
}

/**
 * 긴 카카오 카테고리를
 * 카드 표시용으로 줄임
 *
 * 음식점 > 한식 > 육류,고기 > 곱창,막창
 * → 한식 · 곱창,막창
 */
function getDisplayCategory(category: string): string {
  const parts = category
    .split(">")
    .map((part) => part.trim())
    .filter(Boolean);

  if (parts.length >= 3) {
    return `${parts[1]} · ${parts[parts.length - 1]}`;
  }

  return parts.join(" · ");
}

interface ParsedSearchQuery {
  locationKeyword: string | null;
  placeKeyword: string;
}

/**
 * 검색어를 지역과 장소 키워드로 나눔
 *
 * 동래 맛집     → 지역: 동래, 장소: 없음
 * 전포 디저트   → 지역: 전포, 장소: 디저트
 * 부산진구 초밥 → 지역: 부산진구, 장소: 초밥
 * 스타벅스      → 지역 없음, 장소: 스타벅스
 */
function parseSearchQuery(searchQuery: string): ParsedSearchQuery {
  const words = searchQuery.trim().split(/\s+/).filter(Boolean);

  if (words.length === 0) {
    return {
      locationKeyword: null,
      placeKeyword: "",
    };
  }

  const genericFoodKeywords = new Set(["맛집", "음식점", "식당", "먹거리"]);

  /**
   * 단어가 두 개 이상이면
   * 첫 번째 단어를 지역 후보로 사용
   */
  if (words.length >= 2) {
    const [firstWord, ...remainingWords] = words;

    return {
      locationKeyword: firstWord,
      placeKeyword: remainingWords
        .filter((word) => !genericFoodKeywords.has(word))
        .join(" "),
    };
  }

  /**
   * 단어가 하나면 일반 키워드 검색
   */
  return {
    locationKeyword: null,
    placeKeyword: words[0],
  };
}

function SearchPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get("q") ?? "";

  const [query, setQuery] = useState(initialQuery);

  const [activeCategory, setActiveCategory] = useState("전체");

  const [results, setResults] = useState<KakaoRestaurant[]>([]);

  /**
   * 다음 검색 페이지를 불러오는 중인지 여부
   */
  const [loadingMore, setLoadingMore] = useState(false);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState("");

  const [hotPlaces, setHotPlaces] = useState<HotPlace[]>([]);

  const [mounted, setMounted] = useState(false);

  /**
   * 현재 마우스를 올린 검색 결과
   */
  const [hoveredPlaceId, setHoveredPlaceId] = useState<string | null>(null);

  /**
   * 사용자가 지도를 직접 움직였을 때
   * "이 지역 다시 검색" 버튼 표시 여부
   */
  const [showResearchButton, setShowResearchButton] = useState(false);

  /**
   * 검색, 현재 위치 이동처럼 코드가 지도를 움직이는 동안에는
   * "이 지역 다시 검색" 버튼을 표시하지 않음
   */
  const programmaticMapMoveRef = useRef(false);

  /**
   * 지도 HTML 영역
   */
  const mapRef = useRef<HTMLDivElement>(null);

  /**
   * 검색 결과 리스트 영역
   */
  const listContainerRef = useRef<HTMLDivElement>(null);

  /**
   * 실제 카카오 지도 객체
   */
  const [map, setMap] = useState<KakaoMap | null>(null);

  /**
   * 현재 표시 중인 마커
   */
  const markersRef = useRef<KakaoOverlay[]>([]);

  /**
   * 현재 카카오 검색의 페이지네이션 객체
   */
  const paginationRef = useRef<KakaoPagination | null>(null);

  /**
   * 현재 위치 검색의
   * 음식점 페이지네이션
   */
  const nearbyRestaurantPaginationRef = useRef<KakaoPagination | null>(null);

  /**
   * 현재 위치 검색의
   * 카페 페이지네이션
   */
  const nearbyCafePaginationRef = useRef<KakaoPagination | null>(null);

  /**
   * 현재 지도 전체 검색의 음식점 페이지네이션
   */
  const restaurantPaginationRef = useRef<KakaoPagination | null>(null);

  /**
   * 현재 지도 전체 검색의 카페 페이지네이션
   */
  const cafePaginationRef = useRef<KakaoPagination | null>(null);

  /**
   * 전체 검색에서 음식점·카페 다음 페이지 요청 중
   * 아직 완료되지 않은 요청 개수
   */
  const pendingLoadMoreCountRef = useRef(0);

  /**
   * 현재 요청이 다음 페이지 요청인지 여부
   */
  const isLoadingMoreRef = useRef(false);

  /**
   * 현재 카카오 검색에 다음 페이지가 있는지 여부
   */
  const [hasMore, setHasMore] = useState(false);

  /**
   * 다음 검색에서만 지도 범위를 변경
   */
  const fitBoundsOnNextResultRef = useRef(false);

  /**
   * 현재 실행 중인 검색 종류
   */
  const searchModeRef = useRef<
    "keyword" | "current-map-all" | "current-map-category" | "nearby" | null
  >(null);

  /**
   * 검색 요청 순서를 구분하기 위한 ID
   *
   * 새로운 검색이 시작될 때마다 증가하며,
   * 가장 최근 요청의 콜백만 상태를 변경할 수 있음
   */
  const latestSearchRequestIdRef = useRef(0);

  const createSearchRequestId = () => {
    latestSearchRequestIdRef.current += 1;

    return latestSearchRequestIdRef.current;
  };

  const isLatestSearchRequest = (requestId: number) =>
    latestSearchRequestIdRef.current === requestId;

  /**
   * 새로운 검색을 시작할 때
   * 이전 페이지네이션 상태 초기화
   */
  const resetPagination = () => {
    paginationRef.current = null;

    restaurantPaginationRef.current = null;
    cafePaginationRef.current = null;

    nearbyRestaurantPaginationRef.current = null;
    nearbyCafePaginationRef.current = null;

    pendingLoadMoreCountRef.current = 0;
    isLoadingMoreRef.current = false;

    searchModeRef.current = null;

    setHasMore(false);
    setLoadingMore(false);
  };

  /**
   * 현재 지도 전체 검색의
   * 음식점·카페 다음 페이지 요청이
   * 모두 끝났을 때 호출
   */
  const finishCurrentMapLoadMore = () => {
    pendingLoadMoreCountRef.current -= 1;

    if (pendingLoadMoreCountRef.current > 0) {
      return;
    }

    pendingLoadMoreCountRef.current = 0;

    isLoadingMoreRef.current = false;

    setLoadingMore(false);

    const restaurantHasMore =
      restaurantPaginationRef.current?.hasNextPage ?? false;

    const cafeHasMore = cafePaginationRef.current?.hasNextPage ?? false;

    setHasMore(restaurantHasMore || cafeHasMore);
  };

  /**
   * 현재 위치 음식점·카페 다음 페이지 요청 중
   * 한 요청이 완료될 때마다 호출
   */
  const finishNearbyLoadMore = () => {
    pendingLoadMoreCountRef.current -= 1;

    if (pendingLoadMoreCountRef.current > 0) {
      return;
    }

    pendingLoadMoreCountRef.current = 0;
    isLoadingMoreRef.current = false;

    setLoadingMore(false);

    const restaurantHasMore =
      nearbyRestaurantPaginationRef.current?.hasNextPage ?? false;

    const cafeHasMore = nearbyCafePaginationRef.current?.hasNextPage ?? false;

    setHasMore(restaurantHasMore || cafeHasMore);
  };

  /**
   * Hydration mismatch 방지
   */
  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      setMounted(true);
    });

    return () => cancelAnimationFrame(raf);
  }, []);

  /**
   * 카카오맵 SDK 로딩
   */
  useEffect(() => {
    if (!mapRef.current || typeof window === "undefined") {
      return;
    }

    const kakaoKey = process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

    /**
     * 실제 지도 생성
     */
    const initMap = () => {
      if (!mapRef.current || !window.kakao?.maps) {
        return;
      }

      /**
       * 처음에는 대한민국 전체가
       * 대략 보이도록 생성
       */
      const kakaoMap = new window.kakao.maps.Map(mapRef.current, {
        center: new window.kakao.maps.LatLng(36.5, 127.8),
        level: 13,
      }) as KakaoMap;

      setMap(kakaoMap);

      const mapsWithEvent = window.kakao.maps as typeof window.kakao.maps & {
        event: {
          addListener: (
            target: unknown,
            eventName: string,
            handler: () => void,
          ) => void;
        };
      };

      mapsWithEvent.event.addListener(kakaoMap, "dragend", () => {
        if (programmaticMapMoveRef.current) {
          return;
        }

        setShowResearchButton(true);
      });

      mapsWithEvent.event.addListener(kakaoMap, "zoom_changed", () => {
        if (programmaticMapMoveRef.current) {
          return;
        }

        setShowResearchButton(true);
      });

      /**
       * 위치 권한이 있으면
       * 초기 화면을 현재 위치로 이동
       *
       * 여기서는 검색은 하지 않고
       * 지도 이동만 함
       */
      if (!navigator.geolocation) {
        return;
      }

      navigator.geolocation.getCurrentPosition(
        (position) => {
          if (!window.kakao?.maps) {
            return;
          }

          const currentPosition = new window.kakao.maps.LatLng(
            position.coords.latitude,
            position.coords.longitude,
          );

          kakaoMap.setCenter(currentPosition);

          kakaoMap.setLevel(5);
        },

        () => {},

        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 60000,
        },
      );
    };

    const loadMap = () => {
      if (window.kakao?.maps) {
        window.kakao.maps.load(initMap);
      }
    };

    /**
     * SDK가 이미 로드된 경우
     */
    if (window.kakao?.maps) {
      loadMap();

      return;
    }

    /**
     * script 태그가 이미 있는 경우
     */
    const existing = document.getElementById(
      "kakao-map-sdk",
    ) as HTMLScriptElement | null;

    if (existing) {
      existing.addEventListener("load", loadMap);

      const check = setInterval(() => {
        if (window.kakao?.maps) {
          clearInterval(check);

          loadMap();
        }
      }, 100);

      return () => {
        clearInterval(check);

        existing.removeEventListener("load", loadMap);
      };
    }

    /**
     * 카카오 JS 키 없음
     */
    if (!kakaoKey) {
      const raf = requestAnimationFrame(() => {
        setError(
          "카카오맵 JS 키가 설정되지 않았습니다. .env.local을 확인하세요.",
        );
      });

      return () => cancelAnimationFrame(raf);
    }

    /**
     * SDK script 생성
     */
    const script = document.createElement("script");

    script.id = "kakao-map-sdk";

    script.src =
      `https://dapi.kakao.com/v2/maps/sdk.js` +
      `?appkey=${kakaoKey}` +
      `&libraries=services` +
      `&autoload=false`;

    script.async = true;
    script.crossOrigin = "anonymous";
    script.referrerPolicy = "origin";

    script.onload = loadMap;

    script.onerror = () => {
      setError(
        "카카오맵 SDK를 불러오지 못했습니다. JS 키와 도메인 등록을 확인하세요.",
      );
    };

    document.head.appendChild(script);
  }, []);

  /**
   * 오늘의 핫플 조회
   */
  useEffect(() => {
    const loadHotPlaces = async () => {
      const res = await apiFetchJson<HotPlace[]>(
        "/api/v1/restaurants/today-hot",
      );

      if (res.ok && res.data) {
        setHotPlaces(res.data);
      }
    };

    loadHotPlaces();
  }, []);

  /**
   * 검색 결과가 바뀌면
   * 번호가 표시된 지도 마커를 다시 생성
   */
  useEffect(() => {
    const maps = window.kakao?.maps;

    if (!map || !maps) {
      return;
    }

    /**
     * 기존 마커 제거
     */
    markersRef.current.forEach((marker) => {
      marker.setMap(null);
    });

    markersRef.current = [];

    if (results.length === 0) {
      return;
    }

    const bounds = new maps.LatLngBounds() as KakaoLatLngBounds;

    results.forEach((restaurant, index) => {
      const position = new maps.LatLng(restaurant.lat, restaurant.lng);

      /**
       * 숫자 마커 DOM 생성
       */
      const markerElement = document.createElement("button");

      markerElement.type = "button";
      markerElement.setAttribute(
        "aria-label",
        `${index + 1}번 ${restaurant.name}`,
      );
      markerElement.dataset.placeId = restaurant.kakaoPlaceId;

      markerElement.style.width = "34px";
      markerElement.style.height = "34px";
      markerElement.style.borderRadius = "9999px";
      markerElement.style.border = "3px solid white";
      markerElement.style.background = "#ff6b1a";
      markerElement.style.color = "white";
      markerElement.style.fontSize = "13px";
      markerElement.style.fontWeight = "700";
      markerElement.style.boxShadow = "0 3px 10px rgba(0, 0, 0, 0.25)";
      markerElement.style.cursor = "pointer";
      markerElement.style.transition =
        "transform 150ms ease, box-shadow 150ms ease";

      markerElement.textContent = String(index + 1);

      /**
       * 마커 hover 시 크기 강조
       */
      markerElement.addEventListener("mouseenter", () => {
        setHoveredPlaceId(restaurant.kakaoPlaceId);
      });

      markerElement.addEventListener("mouseleave", () => {
        setHoveredPlaceId(null);
      });

      markerElement.addEventListener("click", () => {
        document
          .getElementById(`place-${restaurant.kakaoPlaceId}`)
          ?.scrollIntoView({
            behavior: "smooth",
            block: "center",
          });
      });

      const overlay = new maps.CustomOverlay({
        position,
        content: markerElement,
        map,
        clickable: true,
        xAnchor: 0.5,
        yAnchor: 1,
        zIndex: 3,
      }) as KakaoOverlay;

      markersRef.current.push(overlay);

      bounds.extend(position);
    });

    /**
     * 일반 키워드 검색일 때만
     * 모든 마커가 보이도록 지도 범위 조정
     */
    if (fitBoundsOnNextResultRef.current) {
      programmaticMapMoveRef.current = true;
      setShowResearchButton(false);

      map.setBounds(bounds);
      fitBoundsOnNextResultRef.current = false;

      window.setTimeout(() => {
        programmaticMapMoveRef.current = false;
      }, 500);
    }

    /**
     * 컴포넌트가 다시 그려지거나 사라질 때
     * 기존 커스텀 오버레이 제거
     */
    return () => {
      markersRef.current.forEach((marker) => {
        marker.setMap(null);
      });

      markersRef.current = [];
    };
  }, [map, results]);

  /**
   * 검색 결과 목록 또는 마커에 마우스를 올리면
   * 해당 번호 마커를 강조
   */
  useEffect(() => {
    results.forEach((restaurant, index) => {
      const overlay = markersRef.current[index];

      if (!overlay) {
        return;
      }

      /**
       * CustomOverlay 내부 DOM을 직접 찾기 어려우므로
       * 번호 마커에 식당 ID를 부여해서 검색
       */
      const markerElement = document.querySelector<HTMLButtonElement>(
        `[data-place-id="${restaurant.kakaoPlaceId}"]`,
      );

      if (!markerElement) {
        return;
      }

      const isActive = restaurant.kakaoPlaceId === hoveredPlaceId;

      markerElement.style.transform = isActive
        ? "scale(1.28) translateY(-2px)"
        : "scale(1)";

      markerElement.style.boxShadow = isActive
        ? "0 6px 18px rgba(0, 0, 0, 0.35)"
        : "0 3px 10px rgba(0, 0, 0, 0.25)";

      markerElement.style.zIndex = isActive ? "10" : "1";
    });
  }, [hoveredPlaceId, results]);

  /**
   * 지역 키워드를 검색해서
   * 지도를 해당 위치로 이동
   *
   * 예:
   * 동래, 서면, 전포, 해운대
   *
   * 이동 성공 시 true
   * 이동 실패 시 false
   */
  const moveMapToLocation = useCallback(
    async (locationKeyword: string): Promise<boolean> => {
      if (!map) {
        setError("지도를 불러오는 중입니다.");
        return false;
      }

      const maps = window.kakao?.maps;
      const services = maps?.services;

      if (!maps || !services) {
        setError("카카오맵 SDK를 불러오지 못했습니다.");
        return false;
      }

      const places = new services.Places();

      return new Promise((resolve) => {
        places.keywordSearch(
          locationKeyword,
          (data: KakaoPlaceItem[], status: string) => {
            if (status !== services.Status.OK || data.length === 0) {
              resolve(false);
              return;
            }

            const firstResult = data[0];

            const position = new maps.LatLng(
              Number(firstResult.y),
              Number(firstResult.x),
            );

            programmaticMapMoveRef.current = true;
            setShowResearchButton(false);

            map.setCenter(position);
            map.setLevel(5);

            window.setTimeout(() => {
              programmaticMapMoveRef.current = false;
            }, 500);

            resolve(true);
          },
          {
            size: 15,
          },
        );
      });
    },
    [map],
  );

  /**
   * 1. 키워드 검색
   *
   * 예:
   * - 부산 맛집
   * - 서면 맛집
   * - 초밥
   * - 스타벅스
   * - 전포 디저트
   * - 케이크
   */
  const fetchKeywordSearch = useCallback(
    async (
      searchQuery: string,
      requestId = createSearchRequestId(),
    ): Promise<void> => {
      const trimmedQuery = searchQuery.trim();

      if (!trimmedQuery) {
        return;
      }

      if (!isLoadingMoreRef.current) {
        resetPagination();
        searchModeRef.current = "keyword";
      }

      setLoading(!isLoadingMoreRef.current);
      setError("");

      const maps = window.kakao?.maps;

      const services = maps?.services;

      if (!maps || !services) {
        setError("카카오맵 SDK를 불러오지 못했습니다.");

        setLoading(false);

        return;
      }

      const places = new services.Places();

      return new Promise((resolve) => {
        places.keywordSearch(
          trimmedQuery,
          (
            data: KakaoPlaceItem[],
            status: string,
            pagination: KakaoPagination,
          ) => {
            /**
             * 이 검색보다 나중에 실행된 검색이 있으면
             * 오래된 응답은 화면 상태를 변경하지 않음
             */
            if (!isLatestSearchRequest(requestId)) {
              resolve();
              return;
            }

            if (status === services.Status.OK) {
              paginationRef.current = pagination;

              const filteredPlaces = filterPlacesByKeyword(
                data.filter(isFoodOrCafe),
                trimmedQuery,
              );

              const mappedResults = mapKakaoPlaces(filteredPlaces);

              /**
               * 현재 페이지에는 음식점·카페가 없지만
               * 다음 카카오 페이지가 남아 있다면 자동 조회
               *
               * loading 상태는 종료하지 않고
               * 다음 페이지 콜백까지 기다림
               */
              if (mappedResults.length === 0 && pagination.hasNextPage) {
                pagination.nextPage();
                return;
              }

              setHasMore(pagination.hasNextPage);

              /**
               * 다음 페이지 요청이면
               * 기존 결과 뒤에 추가
               */
              if (isLoadingMoreRef.current) {
                if (mappedResults.length > 0) {
                  setResults((previous) =>
                    appendUniquePlaces(previous, mappedResults),
                  );
                }

                /**
                 * 마지막 페이지까지 확인했는데
                 * 추가할 음식점·카페가 없어도 기존 결과는 유지
                 */
                setError("");
              } else if (mappedResults.length === 0) {
                /**
                 * 모든 페이지를 확인한 뒤에도
                 * 음식점·카페가 없는 경우
                 */
                setResults([]);
                setError("검색된 음식점이나 카페가 없습니다.");
              } else {
                /**
                 * 새로운 검색이면 결과 교체
                 */
                setResults(mappedResults);
                setError("");
              }
            } else if (status === services.Status.ZERO_RESULT) {
              setHasMore(false);

              /**
               * 다음 페이지 요청 중 결과가 없으면
               * 기존 목록은 유지
               */
              if (!isLoadingMoreRef.current) {
                setResults([]);
                setError("검색 결과가 없습니다.");
              }
            } else {
              setHasMore(false);

              /**
               * 추가 요청 실패 시에도
               * 이미 표시 중인 결과는 유지
               */
              if (!isLoadingMoreRef.current) {
                setResults([]);
                setError("검색 결과를 불러오지 못했습니다.");
              }
            }

            isLoadingMoreRef.current = false;
            setLoadingMore(false);
            setLoading(false);

            resolve();
          },

          {
            /**
             * 카카오 장소 검색은
             * 한 페이지 최대 15개
             */
            size: 15,
          },
        );
      });
    },
    [],
  );

  /**
   * 현재 검색 방식에 맞는
   * 다음 페이지 불러오기
   */
  const loadMoreResults = () => {
    if (loading || loadingMore || isLoadingMoreRef.current || !hasMore) {
      return;
    }

    /**
     * 일반 키워드 검색 또는
     * 현재 지도 개별 카테고리 검색
     */
    if (
      searchModeRef.current === "keyword" ||
      searchModeRef.current === "current-map-category"
    ) {
      const pagination = paginationRef.current;

      if (!pagination?.hasNextPage) {
        setHasMore(false);
        return;
      }

      isLoadingMoreRef.current = true;

      setLoadingMore(true);
      setError("");

      pagination.nextPage();
      return;
    }

    /**
     * 현재 지도 영역 전체 검색
     *
     * 음식점과 카페의 다음 페이지를
     * 각각 요청
     */
    if (searchModeRef.current === "current-map-all") {
      const paginations: KakaoPagination[] = [];

      const restaurantPagination = restaurantPaginationRef.current;
      const cafePagination = cafePaginationRef.current;

      if (restaurantPagination?.hasNextPage) {
        paginations.push(restaurantPagination);
      }

      if (cafePagination?.hasNextPage) {
        paginations.push(cafePagination);
      }

      if (paginations.length === 0) {
        setHasMore(false);
        return;
      }

      isLoadingMoreRef.current = true;
      pendingLoadMoreCountRef.current = paginations.length;

      setLoadingMore(true);
      setError("");

      paginations.forEach((pagination) => {
        pagination.nextPage();
      });

      return;
    }

    /**
     * 현재 위치 검색
     *
     * 음식점과 카페의 다음 페이지를
     * 각각 요청
     */
    if (searchModeRef.current === "nearby") {
      const paginations: KakaoPagination[] = [];

      const restaurantPagination = nearbyRestaurantPaginationRef.current;

      const cafePagination = nearbyCafePaginationRef.current;

      if (restaurantPagination?.hasNextPage) {
        paginations.push(restaurantPagination);
      }

      if (cafePagination?.hasNextPage) {
        paginations.push(cafePagination);
      }

      if (paginations.length === 0) {
        setHasMore(false);
        return;
      }

      isLoadingMoreRef.current = true;
      pendingLoadMoreCountRef.current = paginations.length;

      setLoadingMore(true);
      setError("");

      paginations.forEach((pagination) => {
        pagination.nextPage();
      });
    }
  };

  /**
   * 검색 결과 목록이 아래쪽에 가까워지면
   * 다음 카카오 페이지 요청
   */
  const handleResultScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const element = event.currentTarget;

    const distanceFromBottom =
      element.scrollHeight - element.scrollTop - element.clientHeight;

    /**
     * 바닥에서 120px 이내로 접근하면 미리 불러오기
     */
    if (distanceFromBottom <= 120 && hasMore) {
      loadMoreResults();
    }
  };

  /**
   * URL q 파라미터로 진입 시
   * 카카오맵 로드 후 자동 검색
   */
  useEffect(() => {
    const run = async () => {
      if (initialQuery && map) {
        fitBoundsOnNextResultRef.current = true;

        await fetchKeywordSearch(initialQuery);
      }
    };
    run();
  }, [initialQuery, map, fetchKeywordSearch]);

  /**
   * 현재 위치 주변
   * 음식점 + 카페 검색
   */
  const fetchNearbyPlaces = (lat: number, lng: number) => {
    /**
     * 현재 위치의 새로운 검색이므로
     * 이전 검색 페이지네이션 초기화
     */
    resetPagination();

    searchModeRef.current = "nearby";

    setLoading(true);
    setError("");

    const maps = window.kakao?.maps;
    const services = maps?.services;

    if (!maps || !services) {
      setError("카카오맵 SDK를 불러오지 못했습니다.");
      setLoading(false);
      return;
    }

    const places = new services.Places();

    const location = new maps.LatLng(lat, lng);

    let restaurantResults: KakaoPlaceItem[] = [];
    let cafeResults: KakaoPlaceItem[] = [];

    let restaurantFinished = false;
    let cafeFinished = false;

    /**
     * 현재 위치의 첫 페이지 음식점·카페 검색이
     * 모두 완료되면 결과를 합침
     */
    const finishInitialSearch = () => {
      if (!restaurantFinished || !cafeFinished) {
        return;
      }

      const mergedResults = mergePlaces(restaurantResults, cafeResults);

      const restaurantHasMore =
        nearbyRestaurantPaginationRef.current?.hasNextPage ?? false;

      const cafeHasMore = nearbyCafePaginationRef.current?.hasNextPage ?? false;

      setHasMore(restaurantHasMore || cafeHasMore);

      if (mergedResults.length === 0) {
        setResults([]);
        setError("현재 위치 주변에 음식점이나 카페가 없습니다.");
      } else {
        setResults(mergedResults);
        setError("");
      }

      setLoading(false);
    };

    /**
     * FD6 = 음식점
     */
    places.categorySearch(
      "FD6",
      (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
        /**
         * 현재 위치 음식점 페이지네이션 저장
         */
        nearbyRestaurantPaginationRef.current = pagination;

        /**
         * 무한스크롤로 요청한 다음 페이지
         */
        if (isLoadingMoreRef.current) {
          if (status === services.Status.OK) {
            const mappedResults = mapKakaoPlaces(data);

            setResults((previous) =>
              appendUniquePlaces(previous, mappedResults),
            );
          }

          finishNearbyLoadMore();
          return;
        }

        /**
         * 첫 페이지
         */
        if (status === services.Status.OK) {
          restaurantResults = data;
        }

        restaurantFinished = true;
        finishInitialSearch();
      },
      {
        location,
        radius: 3000,
        size: 15,
        sort: "distance",
      },
    );

    /**
     * CE7 = 카페
     */
    places.categorySearch(
      "CE7",
      (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
        /**
         * 현재 위치 카페 페이지네이션 저장
         */
        nearbyCafePaginationRef.current = pagination;

        /**
         * 무한스크롤로 요청한 다음 페이지
         */
        if (isLoadingMoreRef.current) {
          if (status === services.Status.OK) {
            const mappedResults = mapKakaoPlaces(data);

            setResults((previous) =>
              appendUniquePlaces(previous, mappedResults),
            );
          }

          finishNearbyLoadMore();
          return;
        }

        /**
         * 첫 페이지
         */
        if (status === services.Status.OK) {
          cafeResults = data;
        }

        cafeFinished = true;
        finishInitialSearch();
      },
      {
        location,
        radius: 3000,
        size: 15,
        sort: "distance",
      },
    );
  };

  /**
   * 현재 지도 영역의
   * 음식점 + 카페 검색
   *
   * 검색창이 비어 있고
   * 전체 카테고리를 선택한 경우 실행
   */
  const fetchCurrentMapPlaces = (requestId = createSearchRequestId()) => {
    if (!map) {
      setError("지도를 불러오는 중입니다.");
      return;
    }

    /**
     * 새로운 전체 검색이므로
     * 기존 페이지네이션 초기화
     */
    resetPagination();
    searchModeRef.current = "current-map-all";

    setLoading(true);
    setError("");

    const services = window.kakao?.maps?.services;

    if (!services) {
      setError("카카오맵 SDK를 불러오지 못했습니다.");
      setLoading(false);
      return;
    }

    const places = new services.Places(map);

    let restaurantResults: KakaoPlaceItem[] = [];
    let cafeResults: KakaoPlaceItem[] = [];

    let restaurantFinished = false;
    let cafeFinished = false;

    /**
     * 첫 페이지 음식점·카페 검색이
     * 모두 끝난 후 결과 합치기
     */
    const finishInitialSearch = () => {
      if (!restaurantFinished || !cafeFinished) {
        return;
      }

      const mergedResults = mergePlaces(restaurantResults, cafeResults);

      const restaurantHasMore =
        restaurantPaginationRef.current?.hasNextPage ?? false;

      const cafeHasMore = cafePaginationRef.current?.hasNextPage ?? false;

      setHasMore(restaurantHasMore || cafeHasMore);

      if (mergedResults.length === 0) {
        setResults([]);
        setError("현재 지도 영역에 음식점이나 카페가 없습니다.");
      } else {
        setResults(mergedResults);
        setError("");
      }

      setLoading(false);
    };

    /**
     * FD6 = 음식점
     */
    places.categorySearch(
      "FD6",
      (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
        if (!isLatestSearchRequest(requestId)) {
          return;
        }

        restaurantPaginationRef.current = pagination;

        /**
         * 무한스크롤로 불러온 다음 페이지
         */
        if (isLoadingMoreRef.current) {
          if (status === services.Status.OK) {
            const mappedResults = mapKakaoPlaces(data);

            setResults((previous) =>
              appendUniquePlaces(previous, mappedResults),
            );
          }

          finishCurrentMapLoadMore();
          return;
        }

        /**
         * 첫 페이지
         */
        if (status === services.Status.OK) {
          restaurantResults = data;
        }

        restaurantFinished = true;
        finishInitialSearch();
      },
      {
        useMapBounds: true,
        size: 15,
      },
    );

    /**
     * CE7 = 카페
     */
    places.categorySearch(
      "CE7",
      (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
        if (!isLatestSearchRequest(requestId)) {
          return;
        }

        cafePaginationRef.current = pagination;

        /**
         * 무한스크롤로 불러온 다음 페이지
         */
        if (isLoadingMoreRef.current) {
          if (status === services.Status.OK) {
            const mappedResults = mapKakaoPlaces(data);

            setResults((previous) =>
              appendUniquePlaces(previous, mappedResults),
            );
          }

          finishCurrentMapLoadMore();
          return;
        }

        /**
         * 첫 페이지
         */
        if (status === services.Status.OK) {
          cafeResults = data;
        }

        cafeFinished = true;
        finishInitialSearch();
      },
      {
        useMapBounds: true,
        size: 15,
      },
    );
  };

  /**
   * 검색어가 없는 상태에서
   * 현재 지도 영역의 선택 카테고리 검색
   */
  const fetchCurrentMapPlacesByCategory = (
    category: string,
    requestId = createSearchRequestId(),
  ) => {
    if (!map) {
      setError("지도를 불러오는 중입니다.");
      return;
    }

    /**
     * 전체는 음식점 + 카페 이중 검색 사용
     */
    if (category === "전체") {
      fetchCurrentMapPlaces(requestId);
      return;
    }

    /**
     * 새로운 카테고리 검색일 때만
     * 이전 페이지네이션 초기화
     *
     * pagination.nextPage()로 들어온 콜백에서는
     * 초기화하면 안 됨
     */
    if (!isLoadingMoreRef.current) {
      resetPagination();
      searchModeRef.current = "current-map-category";
    }

    setLoading(!isLoadingMoreRef.current);
    setError("");

    const services = window.kakao?.maps?.services;

    if (!services) {
      setError("카카오맵 SDK를 불러오지 못했습니다.");
      setLoading(false);
      setLoadingMore(false);
      isLoadingMoreRef.current = false;
      return;
    }

    const places = new services.Places(map);

    places.keywordSearch(
      category,
      (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
        /**
         * 다른 카테고리가 나중에 선택됐다면
         * 이전 카테고리 응답은 무시
         */
        if (!isLatestSearchRequest(requestId)) {
          return;
        }

        paginationRef.current = pagination;

        if (status === services.Status.OK) {
          const foodAndCafe = data.filter(isFoodOrCafe);
          const mappedResults = mapKakaoPlaces(foodAndCafe);

          /**
           * 현재 페이지의 필터 결과가 비어 있고
           * 다음 페이지가 있다면 자동 조회
           */
          if (mappedResults.length === 0 && pagination.hasNextPage) {
            pagination.nextPage();
            return;
          }

          setHasMore(pagination.hasNextPage);

          /**
           * 다음 페이지이면 기존 결과 뒤에 추가
           */
          if (isLoadingMoreRef.current) {
            if (mappedResults.length > 0) {
              setResults((previous) =>
                appendUniquePlaces(previous, mappedResults),
              );
            }

            setError("");
          } else if (mappedResults.length === 0) {
            /**
             * 모든 페이지를 확인한 뒤에도
             * 유효한 결과가 없는 경우
             */
            setResults([]);
            setError(`현재 지도 영역에 ${category} 검색 결과가 없습니다.`);
          } else {
            /**
             * 첫 검색 결과로 교체
             */
            setResults(mappedResults);
            setError("");
          }
        } else if (status === services.Status.ZERO_RESULT) {
          setHasMore(false);

          /**
           * 다음 페이지에서 결과가 없는 경우에는
           * 기존 결과를 지우지 않음
           */
          if (!isLoadingMoreRef.current) {
            setResults([]);
            setError(`현재 지도 영역에 ${category} 검색 결과가 없습니다.`);
          }
        } else {
          setHasMore(false);

          if (!isLoadingMoreRef.current) {
            setResults([]);
            setError("검색 결과를 불러오지 못했습니다.");
          }
        }

        isLoadingMoreRef.current = false;
        setLoadingMore(false);
        setLoading(false);
      },
      {
        useMapBounds: true,
        size: 15,
      },
    );
  };

  /**
   * 현재 지도 영역에서
   * 특정 키워드로 음식점·카페 검색
   *
   * 예:
   * 전포로 지도 이동 후 "디저트" 검색
   * 부산진구로 지도 이동 후 "초밥" 검색
   */
  const fetchKeywordInCurrentMap = (
    keyword: string,
    category: string,
  ): Promise<void> => {
    if (!map) {
      setError("지도를 불러오는 중입니다.");
      return Promise.resolve();
    }

    const trimmedKeyword = keyword.trim();

    /**
     * 현재 지도 영역에서 실행하는 새로운 키워드 검색이므로
     * 이전 검색의 페이지네이션 상태 초기화
     */
    resetPagination();
    searchModeRef.current = "current-map-category";

    setLoading(true);
    setError("");

    const services = window.kakao?.maps?.services;

    if (!services) {
      setError("카카오맵 SDK를 불러오지 못했습니다.");
      setLoading(false);
      return Promise.resolve();
    }

    const places = new services.Places(map);

    const searchKeyword =
      category === "전체" ? trimmedKeyword : `${trimmedKeyword} ${category}`;

    return new Promise((resolve) => {
      places.keywordSearch(
        searchKeyword,
        (
          data: KakaoPlaceItem[],
          status: string,
          pagination: KakaoPagination,
        ) => {
          /**
           * 현재 지도 키워드 검색의 페이지네이션 저장
           */
          paginationRef.current = pagination;

          if (status === services.Status.OK) {
            const filteredPlaces = filterPlacesByKeyword(
              data.filter(isFoodOrCafe),
              trimmedKeyword,
            );

            const mappedResults = mapKakaoPlaces(filteredPlaces);

            /**
             * 현재 페이지에는 음식점·카페가 없지만
             * 다음 페이지가 있다면 자동으로 계속 조회
             */
            if (mappedResults.length === 0 && pagination.hasNextPage) {
              pagination.nextPage();
              return;
            }

            setHasMore(pagination.hasNextPage);

            /**
             * 무한스크롤로 요청한 다음 페이지이면
             * 기존 결과 뒤에 추가
             */
            if (isLoadingMoreRef.current) {
              if (mappedResults.length > 0) {
                setResults((previous) =>
                  appendUniquePlaces(previous, mappedResults),
                );
              }

              setError("");
            } else if (mappedResults.length === 0) {
              /**
               * 모든 페이지를 확인한 뒤에도
               * 음식점·카페 결과가 없는 경우
               */
              setResults([]);
              setError(
                `현재 지도 영역에 ${searchKeyword} 검색 결과가 없습니다.`,
              );
            } else {
              /**
               * 새로운 검색의 첫 유효 결과로 교체
               */
              setResults(mappedResults);
              setError("");
            }
          } else if (status === services.Status.ZERO_RESULT) {
            setHasMore(false);

            /**
             * 다음 페이지 결과가 없는 경우에는
             * 기존 검색 결과를 지우지 않음
             */
            if (!isLoadingMoreRef.current) {
              setResults([]);
              setError(
                `현재 지도 영역에 ${searchKeyword} 검색 결과가 없습니다.`,
              );
            }
          } else {
            setHasMore(false);

            /**
             * 다음 페이지 요청 실패 시에도
             * 기존 검색 결과는 유지
             */
            if (!isLoadingMoreRef.current) {
              setResults([]);
              setError("검색 결과를 불러오지 못했습니다.");
            }
          }

          isLoadingMoreRef.current = false;
          setLoadingMore(false);
          setLoading(false);

          resolve();
        },
        {
          useMapBounds: true,
          size: 15,
        },
      );
    });
  };

  /**
   * 현재 위치 버튼
   *
   * 1. GPS 좌표 획득
   * 2. 지도 이동
   * 3. 주변 음식점 + 카페 검색
   */
  const handleCurrentLocation = () => {
    if (!navigator.geolocation) {
      alert("현재 위치를 사용할 수 없는 브라우저입니다.");

      return;
    }

    if (!map) {
      alert("지도를 불러오는 중입니다.");

      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude;

        const lng = position.coords.longitude;

        const maps = window.kakao?.maps;

        if (!maps) {
          return;
        }

        const center = new maps.LatLng(lat, lng);

        /**
         * 현재 위치로 지도 이동
         */
        programmaticMapMoveRef.current = true;
        setShowResearchButton(false);

        map.setCenter(center);
        map.setLevel(4);

        window.setTimeout(() => {
          programmaticMapMoveRef.current = false;
        }, 500);

        /**
         * 현재 위치 주변의
         * 음식점 + 카페 검색
         */
        fetchNearbyPlaces(lat, lng);
      },

      () => {
        alert("현재 위치를 가져올 수 없습니다.");
      },

      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000,
      },
    );
  };

  /**
   * 사용자가 이동한 현재 지도 영역에서 다시 검색
   *
   * 검색어 없음
   * → 선택 카테고리로 현재 지도 검색
   *
   * 동래 맛집
   * → 지역명은 다시 사용하지 않고 현재 지도에서 음식점 검색
   *
   * 전포 디저트
   * → 지역명은 제외하고 현재 지도에서 디저트 검색
   *
   * 스타벅스
   * → 현재 지도에서 스타벅스 검색
   */
  const handleResearchCurrentArea = async () => {
    setShowResearchButton(false);
    fitBoundsOnNextResultRef.current = false;
    setError("");

    const trimmedQuery = query.trim();

    /**
     * 검색창이 비어 있으면
     * 현재 카테고리만 적용
     */
    if (!trimmedQuery) {
      fetchCurrentMapPlacesByCategory(activeCategory);
      return;
    }

    const { locationKeyword, placeKeyword } = parseSearchQuery(trimmedQuery);

    /**
     * "동래 맛집"처럼 지역명만 남은 검색
     *
     * 사용자가 이미 다른 영역으로 지도를 이동했으므로
     * 동래로 다시 돌아가지 않고 현재 지도 영역을 검색
     */
    if (locationKeyword && !placeKeyword) {
      fetchCurrentMapPlacesByCategory(activeCategory);
      return;
    }

    /**
     * "전포 디저트", "부산진구 초밥"
     *
     * 지역명은 제외하고 현재 지도에서
     * 디저트 또는 초밥만 검색
     */
    if (locationKeyword && placeKeyword) {
      await fetchKeywordInCurrentMap(placeKeyword, activeCategory);

      return;
    }

    /**
     * "스타벅스", "초밥", "파스타"
     *
     * 단일 검색어를 현재 지도 영역에서 검색
     */
    await fetchKeywordInCurrentMap(trimmedQuery, activeCategory);
  };

  /**
   * 검색어 변경
   */
  const handleQueryChange = (value: string) => {
    setQuery(value);
  };

  /**
   * 카테고리 변경
   */
  const handleCategoryChange = async (category: string) => {
    /**
     * 카테고리를 클릭할 때마다 새로운 요청 ID 발급
     *
     * 이후 가장 마지막으로 발급된 ID의 응답만
     * 검색 결과를 변경할 수 있음
     */
    const requestId = createSearchRequestId();

    setShowResearchButton(false);
    setActiveCategory(category);

    /**
     * 카테고리를 변경해도
     * 현재 지도 위치는 유지
     */
    fitBoundsOnNextResultRef.current = false;

    const trimmedQuery = query.trim();

    /**
     * 검색어가 없는 경우
     * 현재 지도 영역에서 카테고리 검색
     */
    if (!trimmedQuery) {
      fetchCurrentMapPlacesByCategory(category, requestId);
      return;
    }

    /**
     * 검색어가 있는 경우
     * 검색어 + 카테고리로 재검색
     */
    const searchKeyword =
      category === "전체" ? trimmedQuery : `${trimmedQuery} ${category}`;

    await fetchKeywordSearch(searchKeyword, requestId);
  };

  /**
   * 검색 버튼
   *
   * 검색어 있음
   * → 지역 + 장소 키워드 분석
   *
   * 지역 후보가 있음
   * → 지역으로 지도 이동
   * → 현재 지도 영역에서 검색
   *
   * 지역 후보가 없음
   * → 기존 키워드 검색
   *
   * 검색어 없음
   * → 현재 지도 영역의 선택 카테고리 검색
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setShowResearchButton(false);
    setError("");

    const trimmedQuery = query.trim();

    /**
     * 검색어가 없는 경우
     */
    if (!trimmedQuery) {
      fitBoundsOnNextResultRef.current = false;

      fetchCurrentMapPlacesByCategory(activeCategory);
      return;
    }

    const { locationKeyword, placeKeyword } = parseSearchQuery(trimmedQuery);

    /**
     * "동래 맛집", "전포 디저트"처럼
     * 단어가 두 개 이상인 경우
     */
    if (locationKeyword) {
      setLoading(true);

      /**
       * 지역으로 지도 위치를 직접 바꾸기 때문에
       * 결과 마커 기준 setBounds는 실행하지 않음
       */
      fitBoundsOnNextResultRef.current = false;

      const moved = await moveMapToLocation(locationKeyword);

      if (moved) {
        /**
         * 카카오 지도 이동 반영을 위해
         * 짧게 기다린 후 검색
         */
        await new Promise((resolve) => {
          setTimeout(resolve, 300);
        });

        /**
         * 동래 맛집
         * → 장소 키워드가 없으므로
         * 현재 지도 영역의 음식점·카페 검색
         */
        if (!placeKeyword) {
          fetchCurrentMapPlacesByCategory(activeCategory);
          return;
        }

        /**
         * 전포 디저트
         * → 현재 지도 영역에서 디저트 검색
         */
        await fetchKeywordInCurrentMap(placeKeyword, activeCategory);

        return;
      }

      /**
       * 지역 이동 실패 시
       * 기존 전체 키워드 검색으로 대체
       */
      setLoading(false);
    }

    /**
     * 스타벅스, 초밥, 파스타처럼
     * 단어가 하나인 경우 기존 검색
     */
    fitBoundsOnNextResultRef.current = true;

    const searchKeyword =
      activeCategory === "전체"
        ? trimmedQuery
        : `${trimmedQuery} ${activeCategory}`;

    await fetchKeywordSearch(searchKeyword);
  };

  /**
   * 식당 또는 카페 카드 클릭
   */
  const handleSelect = async (restaurant: KakaoRestaurant) => {
    /**
     * kakaoPlaceId로
     * 이미 DB에 있는지 확인
     */
    const findRes = await apiFetchJson<{
      id: number;
    }>(`/api/v1/restaurants?kakaoPlaceId=${restaurant.kakaoPlaceId}`);

    if (findRes.ok && findRes.data) {
      router.push(`/restaurant/${findRes.data.id}`);

      return;
    }

    /**
     * DB에 없으면 저장
     */
    const saveRes = await apiFetchJson<{
      id: number;
    }>("/api/v1/restaurants", {
      method: "POST",

      body: JSON.stringify({
        kakaoPlaceId: restaurant.kakaoPlaceId,

        name: restaurant.name,

        categoryName: restaurant.category,

        address: restaurant.address,

        roadAddress: restaurant.roadAddress,

        region1: restaurant.region1,

        region2: restaurant.region2,

        region3: restaurant.region3,

        phone: restaurant.phone,

        lat: restaurant.lat,

        lng: restaurant.lng,
      }),
    });

    if (saveRes.ok && saveRes.data) {
      router.push(`/restaurant/${saveRes.data.id}`);
    } else {
      alert(saveRes.message || "장소 저장에 실패했습니다.");
    }
  };

  return (
    <AppShell
      fullWidth
      leftSidebar={
        <div className="sticky top-28 space-y-5">
          <SidebarProfile />

          <SidebarCard title="오늘의 핫플">
            <div className="space-y-4">
              {hotPlaces.length === 0 ? (
                <p className="text-sm text-muted">등록된 식당이 없습니다.</p>
              ) : (
                hotPlaces.map((place, index) => (
                  <Link
                    key={place.id}
                    href={`/restaurant/${place.id}`}
                    className="flex items-start gap-3 rounded-lg p-2 -m-2 transition-colors hover:bg-primary/5"
                  >
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
                      {index + 1}
                    </span>

                    <div>
                      <p className="text-base font-bold text-ink">
                        {place.name}
                      </p>

                      <p className="text-sm text-muted">
                        {categoryLabelMap[place.category] || place.category}

                        {" · "}

                        {place.region2 || "-"}
                      </p>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </SidebarCard>
        </div>
      }
    >
      <div className="relative h-[calc(100vh-6.5rem)] overflow-hidden rounded-2xl border border-hairline-soft">
        {/* 지도 */}
        <div ref={mapRef} className="absolute inset-0 bg-surface-strong" />
        {/* 이 지역 다시 검색 */}
        {showResearchButton && (
          <button
            type="button"
            onClick={handleResearchCurrentArea}
            disabled={loading}
            className="absolute left-[calc(50%+200px)] top-5 z-30 -translate-x-1/2 rounded-full border border-hairline-soft bg-white px-5 py-2.5 text-sm font-bold text-ink shadow-lg transition-all hover:border-primary/30 hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? "검색 중..." : "이 지역 다시 검색"}
          </button>
        )}

        {/* 검색 결과 세로 목록 */}
        <div className="absolute bottom-4 left-4 top-4 z-20 w-[400px]">
          <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-hairline-soft bg-surface/95 shadow-xl backdrop-blur">
            {/* 검색창 + 카테고리 */}
            <form
              onSubmit={handleSubmit}
              className="shrink-0 border-b border-hairline-soft bg-surface px-4 py-4"
            >
              {/* 검색 입력 */}
              <div className="flex items-center gap-2 rounded-xl border border-hairline bg-surface-soft px-3 py-2.5">
                <Search className="h-5 w-5 shrink-0 text-muted" />

                {mounted && (
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => handleQueryChange(e.target.value)}
                    className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-hidden placeholder:text-muted-soft"
                    placeholder="지역, 식당, 카페, 디저트 검색"
                  />
                )}
                {query && (
                  <button
                    type="button"
                    onClick={() => {
                      createSearchRequestId();
                      setQuery("");
                      setResults([]);
                      setError("");
                      setHoveredPlaceId(null);
                      resetPagination();
                    }}
                    aria-label="검색어 지우기"
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-white hover:text-ink"
                  >
                    <X className="h-4 w-4" />
                  </button>
                )}

                <button
                  type="button"
                  onClick={handleCurrentLocation}
                  disabled={loading}
                  aria-label="현재 위치"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-white text-primary transition-colors hover:bg-surface-strong disabled:opacity-60"
                >
                  <Navigation className="h-4 w-4" />
                </button>

                <button
                  type="submit"
                  disabled={loading}
                  className="shrink-0 rounded-lg bg-primary px-3 py-2 text-xs font-bold text-white transition-colors hover:bg-primary-active disabled:opacity-70"
                >
                  {loading ? "검색 중" : "검색"}
                </button>
              </div>

              {/* 카테고리 */}
              <div className="mt-3 flex gap-1.5 overflow-x-auto pb-1">
                {categories.map((category) => (
                  <button
                    key={category}
                    type="button"
                    onClick={() => handleCategoryChange(category)}
                    className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
                      category === activeCategory
                        ? "bg-primary text-white"
                        : "bg-surface-soft text-muted hover:bg-hairline-soft"
                    }`}
                  >
                    {category}
                  </button>
                ))}
              </div>
            </form>
            {/* 목록 헤더 */}
            <div className="shrink-0 border-b border-hairline-soft px-5 py-3">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-ink">검색 결과</h2>

                  <p className="mt-1 text-xs text-muted">
                    {results[0]?.region2
                      ? `${results[0].region2} 주변 장소`
                      : "현재 지역에서 찾은 장소"}
                  </p>
                </div>

                <span className="rounded-full bg-primary/10 px-3 py-1 text-sm font-bold text-primary">
                  {results.length}개
                </span>
              </div>
            </div>

            {/* 검색 결과 */}
            <div
              ref={listContainerRef}
              onScroll={handleResultScroll}
              className="min-h-0 flex-1 overflow-y-auto"
            >
              {loading && results.length === 0 ? (
                <div className="flex h-full items-center justify-center px-6">
                  <p className="text-sm text-muted">
                    검색 결과를 불러오는 중입니다.
                  </p>
                </div>
              ) : error ? (
                <div className="flex h-full items-center justify-center px-6">
                  <p className="text-center text-sm text-red-500">{error}</p>
                </div>
              ) : results.length === 0 ? (
                <div className="flex h-full items-center justify-center px-6">
                  <p className="text-center text-sm text-muted">
                    검색 결과가 없습니다.
                  </p>
                </div>
              ) : (
                <div className="divide-y divide-hairline-soft">
                  {results.map((restaurant, index) => (
                    <button
                      id={`place-${restaurant.kakaoPlaceId}`}
                      key={restaurant.kakaoPlaceId}
                      type="button"
                      onMouseEnter={() => {
                        setHoveredPlaceId(restaurant.kakaoPlaceId);
                      }}
                      onMouseLeave={() => {
                        setHoveredPlaceId(null);
                      }}
                      onFocus={() => {
                        setHoveredPlaceId(restaurant.kakaoPlaceId);
                      }}
                      onBlur={() => {
                        setHoveredPlaceId(null);
                      }}
                      onClick={() => handleSelect(restaurant)}
                      className={`group flex w-full gap-4 px-4 py-4 text-left transition-colors ${
                        hoveredPlaceId === restaurant.kakaoPlaceId
                          ? "bg-primary/5"
                          : "hover:bg-surface-soft"
                      }`}
                    >
                      {/* 장소 순번 */}
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">
                        {index + 1}
                      </div>

                      {/* 장소 정보 */}
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-3">
                          <h3 className="line-clamp-1 text-sm font-bold text-ink group-hover:text-primary">
                            {restaurant.name}
                          </h3>

                          <span className="shrink-0 text-[11px] text-muted">
                            {restaurant.region2}
                          </span>
                        </div>

                        <p className="mt-1 line-clamp-1 text-xs text-muted">
                          {getDisplayCategory(restaurant.category)}
                        </p>

                        <div className="mt-2 flex items-start gap-1.5">
                          <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted" />

                          <p className="line-clamp-2 text-xs leading-5 text-body">
                            {restaurant.roadAddress || restaurant.address}
                          </p>
                        </div>

                        {restaurant.phone && (
                          <p className="mt-1.5 text-xs text-muted">
                            {restaurant.phone}
                          </p>
                        )}
                      </div>

                      {/* 썸네일 */}
                      <div className="h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-surface-strong">
                        <img
                          src="/restaurant-placeholder.png"
                          alt={restaurant.name}
                          className="h-full w-full object-cover transition-transform group-hover:scale-105"
                        />
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 추가 결과 로딩 */}
            {loadingMore && (
              <div className="shrink-0 border-t border-hairline-soft px-4 py-3">
                <p className="text-center text-xs text-muted">
                  장소를 더 불러오는 중입니다.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}

export default function SearchPageWrapper() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-background" />}>
      <SearchPage />
    </Suspense>
  );
}
