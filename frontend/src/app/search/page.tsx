"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ChevronLeft, ChevronRight, Navigation, Search } from "lucide-react";
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

interface KakaoMarker {
  setMap: (map: unknown | null) => void;
}

interface KakaoMap {
  setCenter: (center: unknown) => void;
  setBounds: (bounds: unknown) => void;
  setLevel: (level: number) => void;
}

interface KakaoLatLngBounds {
  extend: (position: unknown) => void;
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
 * 상단 카테고리 필터
 */
function matchesCategory(item: KakaoRestaurant, category: string): boolean {
  if (category === "전체") {
    return true;
  }

  /**
   * 카페 필터에서는
   * 카페 + 디저트 계열 모두 표시
   */
  if (category === "카페") {
    return (
      item.category.includes("카페") ||
      item.category.includes("디저트") ||
      item.category.includes("베이커리")
    );
  }

  return item.category.includes(category);
}

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

function SearchPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get("q") ?? "";

  const [query, setQuery] = useState(initialQuery);

  const [activeCategory, setActiveCategory] = useState("전체");

  const [results, setResults] = useState<KakaoRestaurant[]>([]);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState("");

  const [hotPlaces, setHotPlaces] = useState<HotPlace[]>([]);

  const [mounted, setMounted] = useState(false);

  /**
   * 검색 결과 페이지네이션
   */
  const [currentPage, setCurrentPage] = useState(0);

  const itemsPerPage = 3;

  /**
   * 지도 HTML 영역
   */
  const mapRef = useRef<HTMLDivElement>(null);

  /**
   * 실제 카카오 지도 객체
   */
  const [map, setMap] = useState<KakaoMap | null>(null);

  /**
   * 현재 표시 중인 마커
   */
  const markersRef = useRef<KakaoMarker[]>([]);

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
      const res = await apiFetchJson<HotPlace[]>("/api/v1/restaurants/today-hot");

      if (res.ok && res.data) {
        setHotPlaces(res.data);
      }
    };

    loadHotPlaces();
  }, []);

  /**
   * 검색 결과 또는
   * 카테고리가 바뀌면
   * 마커 다시 표시
   */
  useEffect(() => {
    if (!map || !window.kakao?.maps) {
      return;
    }

    /**
     * 기존 마커 제거
     */
    markersRef.current.forEach((marker) => {
      marker.setMap(null);
    });

    markersRef.current = [];

    const filteredResults = results.filter((restaurant) =>
      matchesCategory(restaurant, activeCategory),
    );

    if (filteredResults.length === 0) {
      return;
    }

    const bounds = new window.kakao.maps.LatLngBounds() as KakaoLatLngBounds;

    filteredResults.forEach((restaurant) => {
      if (!window.kakao?.maps) {
        return;
      }

      const position = new window.kakao.maps.LatLng(
        restaurant.lat,
        restaurant.lng,
      );

      const marker = new window.kakao.maps.Marker({
        position,
        map,
      }) as KakaoMarker;

      markersRef.current.push(marker);

      bounds.extend(position);
    });

    /**
     * 검색 결과가
     * 지도 화면 안에 들어오도록 조정
     */
    map.setBounds(bounds);
  }, [map, results, activeCategory]);

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
  const fetchKeywordSearch = useCallback(async (searchQuery: string): Promise<void> => {
    const trimmedQuery = searchQuery.trim();

    if (!trimmedQuery) {
      return;
    }

    /**
     * 이전에 카테고리를 선택한 상태에서
     * 새로운 검색을 하면 결과가 숨을 수 있으므로
     * 전체 카테고리로 초기화
     */
    setActiveCategory("전체");

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

    return new Promise((resolve) => {
      places.keywordSearch(
        trimmedQuery,

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          /**
           * 검색 결과 중
           * 음식점·카페 계열만 남김
           */
          const foodAndCafe = data.filter(isFoodOrCafe);

          if (foodAndCafe.length === 0) {
            setResults([]);

            setError("검색된 음식점이나 카페가 없습니다.");
          } else {
            setResults(mapKakaoPlaces(foodAndCafe));

            setError("");
          }
        } else if (status === services.Status.ZERO_RESULT) {
          setResults([]);

          setError("검색 결과가 없습니다.");
        } else {
          setResults([]);

          setError("검색 결과를 불러오지 못했습니다.");
        }

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
}, []);

/**
 * URL q 파라미터로 진입 시
 * 카카오맵 로드 후 자동 검색
 */
  useEffect(() => {
    const run = async () => {
      if (initialQuery && map) {
        await fetchKeywordSearch(initialQuery);
      }
    };
    run();
  }, [initialQuery, map, fetchKeywordSearch]);

  /**
   * 2. 현재 위치 주변
   * 음식점 + 카페 검색
   */
  const fetchNearbyPlaces = (lat: number, lng: number) => {
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
     * 음식점 + 카페 검색이
     * 모두 끝났을 때 실행
     */
    const finishSearch = () => {
      if (!restaurantFinished || !cafeFinished) {
        return;
      }

      const mergedResults = mergePlaces(restaurantResults, cafeResults);

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

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          restaurantResults = data;
        }

        restaurantFinished = true;

        finishSearch();
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

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          cafeResults = data;
        }

        cafeFinished = true;

        finishSearch();
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
   * 3. 현재 지도 영역
   * 음식점 + 카페 검색
   *
   * 검색창이 비어 있을 때
   * 검색 버튼을 누르면 실행
   */
  const fetchCurrentMapPlaces = () => {
    if (!map) {
      setError("지도를 불러오는 중입니다.");

      return;
    }

    setLoading(true);

    setError("");

    const services = window.kakao?.maps?.services;

    if (!services) {
      setError("카카오맵 SDK를 불러오지 못했습니다.");

      setLoading(false);

      return;
    }

    /**
     * 현재 지도 영역과 연결
     */
    const places = new services.Places(map);

    let restaurantResults: KakaoPlaceItem[] = [];

    let cafeResults: KakaoPlaceItem[] = [];

    let restaurantFinished = false;

    let cafeFinished = false;

    /**
     * 음식점 + 카페 결과
     * 모두 받은 후 합치기
     */
    const finishSearch = () => {
      if (!restaurantFinished || !cafeFinished) {
        return;
      }

      const mergedResults = mergePlaces(restaurantResults, cafeResults);

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

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          restaurantResults = data;
        }

        restaurantFinished = true;

        finishSearch();
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

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          cafeResults = data;
        }

        cafeFinished = true;

        finishSearch();
      },

      {
        useMapBounds: true,
        size: 15,
      },
    );
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
        map.setCenter(center);

        map.setLevel(4);

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
   * 검색어 변경
   */
  const handleQueryChange = (value: string) => {
    setQuery(value);
    setCurrentPage(0);
  };

  /**
   * 카테고리 변경
   */
  const handleCategoryChange = (category: string) => {
    setActiveCategory(category);
    setCurrentPage(0);
  };

  /**
   * 검색 버튼
   *
   * 검색어 있음
   * → 키워드 검색
   *
   * 검색어 없음
   * → 현재 지도 영역의
   *   음식점 + 카페 검색
   */
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (query.trim()) {
      fetchKeywordSearch(query);

      return;
    }

    fetchCurrentMapPlaces();
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

  /**
   * 화면에 표시할 결과
   */
  const filtered = results.filter((restaurant) =>
    matchesCategory(restaurant, activeCategory),
  );

  /**
   * 페이지네이션
   */
  const totalPages = Math.ceil(filtered.length / itemsPerPage);

  const currentItems = filtered.slice(
    currentPage * itemsPerPage,
    (currentPage + 1) * itemsPerPage,
  );

  const handlePrevPage = () => {
    setCurrentPage((prev) => Math.max(0, prev - 1));
  };

  const handleNextPage = () => {
    setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1));
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

        {/* 상단 검색창 */}
        <div className="absolute inset-x-4 top-4 z-10 md:inset-x-auto md:left-1/2 md:w-full md:max-w-2xl md:-translate-x-1/2">
          <form
            onSubmit={handleSubmit}
            className="space-y-3 rounded-2xl border border-hairline-soft bg-surface/90 p-4 shadow-lg backdrop-blur"
          >
            {/* 검색 입력 영역 */}
            <div className="flex items-center gap-2 rounded-xl border border-hairline bg-surface-soft px-4 py-2.5">
              <Search className="h-5 w-5 shrink-0 text-muted" />

              {mounted && (
                <input
                  type="text"
                  value={query}
                  onChange={(e) => handleQueryChange(e.target.value)}
                  className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-hidden placeholder:text-muted-soft"
                  placeholder="지역, 식당, 카페, 디저트를 검색하세요"
                />
              )}

              {/* 현재 위치 */}
              <button
                type="button"
                onClick={handleCurrentLocation}
                disabled={loading}
                aria-label="현재 위치"
                className="flex shrink-0 items-center gap-1.5 rounded-lg border border-hairline bg-white px-3 py-1.5 text-xs font-bold text-ink transition-colors hover:bg-surface-strong disabled:opacity-60"
              >
                <Navigation className="h-3.5 w-3.5 text-primary" />

                <span className="hidden sm:inline">현재 위치</span>
              </button>

              {/* 검색 */}
              <button
                type="submit"
                disabled={loading}
                className="shrink-0 rounded-lg bg-primary px-4 py-1.5 text-xs font-bold text-white transition-colors hover:bg-primary-active disabled:opacity-70"
              >
                {loading ? "검색 중..." : "검색"}
              </button>
            </div>

            {/* 카테고리 */}
            <div className="flex flex-wrap justify-center gap-1.5">
              {categories.map((category) => (
                <button
                  key={category}
                  type="button"
                  onClick={() => handleCategoryChange(category)}
                  className={`rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
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
        </div>

        {/* 하단 검색 결과 */}
        <div className="absolute inset-x-0 bottom-0 z-10 px-4 pb-4">
          <div className="rounded-2xl border border-hairline-soft bg-surface/95 p-4 shadow-lg backdrop-blur">
            {error ? (
              <p className="py-4 text-center text-sm text-red-500">{error}</p>
            ) : (
              <>
                <p className="mb-3 text-sm font-bold text-ink">
                  검색 결과{" "}
                  <span className="text-primary">{filtered.length}개</span>
                </p>

                <div className="flex items-stretch gap-3">
                  <button
                    type="button"
                    onClick={handlePrevPage}
                    disabled={currentPage === 0 || currentItems.length === 0}
                    aria-label="이전 검색 결과"
                    className="flex shrink-0 items-center justify-center self-stretch rounded-xl border border-hairline-soft bg-surface px-4 text-muted transition-colors hover:border-primary/30 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <ChevronLeft className="h-7 w-7" />
                  </button>

                  <div className="grid flex-1 grid-cols-3 gap-4">
                    {currentItems.map((restaurant) => (
                      <button
                        key={restaurant.kakaoPlaceId}
                        type="button"
                        onClick={() => handleSelect(restaurant)}
                        className="w-full text-left"
                      >
                        <article className="group flex h-[300px] w-full flex-col overflow-hidden rounded-2xl border border-hairline-soft bg-surface shadow-sm transition-all hover:border-primary/20 hover:shadow-md">
                        {/* 이미지 */}
                        <div className="h-36 w-full shrink-0 overflow-hidden bg-surface-strong">
                          <img
                            src="/restaurant-placeholder.png"
                            alt={restaurant.name}
                            className="h-full w-full object-cover transition-transform group-hover:scale-105"
                          />
                        </div>

                        {/* 장소 정보 */}
                        <div className="flex min-h-0 flex-1 flex-col p-4">
                          <h3 className="line-clamp-1 text-base font-bold text-ink">
                            {restaurant.name}
                          </h3>

                          <p className="mt-1 line-clamp-1 text-xs text-muted">
                            {getDisplayCategory(restaurant.category)}
                          </p>

                          <p className="mt-auto line-clamp-2 pt-3 text-xs leading-5 text-body">
                            {restaurant.roadAddress || restaurant.address}
                          </p>
                        </div>
                        </article>
                      </button>
                    ))}
                  </div>

                  <button
                    type="button"
                    onClick={handleNextPage}
                    disabled={currentPage >= totalPages - 1 || currentItems.length === 0}
                    aria-label="다음 검색 결과"
                    className="flex shrink-0 items-center justify-center self-stretch rounded-xl border border-hairline-soft bg-surface px-4 text-muted transition-colors hover:border-primary/30 hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <ChevronRight className="h-7 w-7" />
                  </button>
                </div>

                {totalPages > 1 && (
                  <p className="mt-3 text-center text-xs text-muted">
                    {currentPage + 1} / {totalPages}
                  </p>
                )}
              </>
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
