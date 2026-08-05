"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  MapPin,
  Utensils,
  Sparkles,
  Shuffle,
  Navigation,
  X,
  ChevronRight,
  Bookmark,
  Check,
  RotateCcw,
  SearchX,
  Home,
  ArrowLeft,
} from "lucide-react";
import AppShell, { SidebarProfile, SidebarCard } from "@/components/AppShell";
import { apiFetchJson } from "@/lib/api";
import { MOOD_TAGS } from "@/lib/mood";
import regionsData from "@/data/regions.json";

const categories = ["한식", "일식", "양식", "중식", "분식", "카페", "치킨", "패스트푸드", "술집", "뷔페", "샤브샤브", "퓨전요리", "아시안", "기타"];

interface HotPlace {
  id: number;
  name: string;
  category: string;
  region2: string;
}

const categoryEmoji: Record<string, string> = {
  "한식": "🍚",
  "일식": "🍣",
  "양식": "🍝",
  "중식": "🍡",
  "분식": "🍢",
  "카페": "☕\uFE0F",
  "아시안": "🍛",
  "치킨": "🍗",
  "패스트푸드": "🍔",
  "술집": "🍺",
  "뷔페": "🍽️",
  "샤브샤브": "🍲",
  "퓨전요리": "🥘",
};
const categoryToEnum: Record<string, string> = {
  "한식": "KOREAN",
  "일식": "JAPANESE",
  "양식": "WESTERN",
  "중식": "CHINESE",
  "분식": "SNACK",
  "카페": "CAFE",
  "아시안": "ASIAN",
  "치킨": "CHICKEN",
  "패스트푸드": "FASTFOOD",
  "술집": "BAR",
  "뷔페": "BUFFET",
  "샤브샤브": "SHABU",
  "퓨전요리": "FUSION",
};

const moods = ["데이트", "혼밥", "회식", "야식", "가족", "친구"];
const sorts = [
  { key: "random", label: "랜덤", icon: Shuffle },
  { key: "distance", label: "거리순", icon: Navigation },
];

type RegionNode = string[] | Record<string, string[]>;
type RegionData = Record<string, RegionNode>;

const typedRegionsData = regionsData as unknown as RegionData;

interface RecommendRestaurant {
  id: number;
  kakaoPlaceId: string;
  name: string;
  category: string;
  address: string;
  roadAddress: string;
  region1: string;
  region2: string;
  region3: string;
  region4: string;
  phone: string;
  lat: number;
  lng: number;
  createdAt: string;
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
  region4: string;
  phone: string;
  lat: number;
  lng: number;
}

interface RecommendItem {
  kakaoPlaceId: string;
  category: string;
  categoryLabel: string;
  distanceMeter: number | null;
}

function categoryLabel(enumValue: string): string {
  const found = Object.entries(categoryToEnum).find(([, v]) => v === enumValue);
  return found ? found[0] : enumValue;
}

/** 오늘의 핫플 목록 — 데스크톱 사이드바와 모바일 흡수 섹션에서 공유 */
function HotPlaceList({ hotPlaces }: { hotPlaces: HotPlace[] }) {
  if (hotPlaces.length === 0) {
    return <p className="text-sm text-muted">등록된 식당이 없습니다.</p>;
  }

  return (
    <div className="space-y-4">
      {hotPlaces.map((p, i) => (
        <Link
          key={p.id}
          href={`/restaurant/${p.id}`}
          className="flex items-start gap-3 rounded-lg p-2 -m-2 transition-colors hover:bg-primary/5"
        >
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
            {i + 1}
          </span>
          <div>
            <p className="text-base font-bold text-ink">{p.name}</p>
            <p className="text-sm text-muted">{categoryLabel(p.category)} · {p.region2 || "-"}</p>
          </div>
        </Link>
      ))}
    </div>
  );
}

export default function RecommendPage() {
  const router = useRouter();

  const [selectedCategory, setSelectedCategory] = useState("한식");
  const [selectedMood, setSelectedMood] = useState("혼밥");
  const [sortBy, setSortBy] = useState("random");

  const [locationModalOpen, setLocationModalOpen] = useState(false);
  const [selectedRegion1, setSelectedRegion1] = useState("서울");
  const [selectedRegion2, setSelectedRegion2] = useState("중구");
  const [selectedRegion3, setSelectedRegion3] = useState("을지로동");
  const [selectedRegion4, setSelectedRegion4] = useState("");

  const [resultModalOpen, setResultModalOpen] = useState(false);
  const [recommendLoading, setRecommendLoading] = useState(false);
  const [recommendError, setRecommendError] = useState("");
  const [distanceFallback, setDistanceFallback] = useState(false);
  const [current, setCurrent] = useState<RecommendRestaurant | null>(null);
  const [hotPlaces, setHotPlaces] = useState<HotPlace[]>([]);

  const mapRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<KakaoMap | null>(null);
  const markerRef = useRef<KakaoMarker | null>(null);
  const placesRef = useRef<KakaoPlaces | null>(null);

  // 추천 후보 풀/소비 큐/이미 본 식당 관리
  const poolRef = useRef<KakaoRestaurant[]>([]);
  const queueRef = useRef<RecommendItem[]>([]);
  const seenIdsRef = useRef<Set<string>>(new Set());
  const [currentLabel, setCurrentLabel] = useState("기타");

  const locationLabel =
    selectedRegion1 +
    (selectedRegion2 === "전체" ? "" : ` ${selectedRegion2}`) +
    (selectedRegion3 === "전체" ? "" : ` ${selectedRegion3}`) +
    (selectedRegion4 && selectedRegion4 !== "전체" ? ` ${selectedRegion4}` : "");

  const region1List = Object.keys(typedRegionsData);

  const getRegion2List = (r1: string): string[] => {
    const node = typedRegionsData[r1];
    if (!node) return [];
    return Object.keys(node);
  };

  const getRegion3Node = (
    r1: string,
    r2: string,
  ): RegionNode | undefined => {
    const node = typedRegionsData[r1];
    if (!node) return undefined;
    return (node as Record<string, RegionNode>)[r2];
  };

  const getRegion3List = (r1: string, r2: string): string[] => {
    const child = getRegion3Node(r1, r2);
    if (!child) return [];
    if (Array.isArray(child)) return child;
    return ["전체", ...Object.keys(child)];
  };

  const getRegion4List = (r1: string, r2: string, r3: string): string[] => {
    const child = getRegion3Node(r1, r2);
    if (!child || Array.isArray(child)) return ["전체"];
    const leaf = (child as Record<string, string[]>)[r3];
    return leaf || ["전체"];
  };

  const handleRegion1Change = (r1: string) => {
    const r2List = getRegion2List(r1);
    const r2 = r2List[0] || "전체";
    const r3List = getRegion3List(r1, r2);
    const r3 = r3List[0] || "전체";
    const r4List = getRegion4List(r1, r2, r3);
    const r4 = r4List[0] || "";

    setSelectedRegion1(r1);
    setSelectedRegion2(r2);
    setSelectedRegion3(r3);
    setSelectedRegion4(r4);
  };

  const handleRegion2Change = (r2: string) => {
    const r3List = getRegion3List(selectedRegion1, r2);
    const r3 = r3List[0] || "전체";
    const r4List = getRegion4List(selectedRegion1, r2, r3);
    const r4 = r4List[0] || "";

    setSelectedRegion2(r2);
    setSelectedRegion3(r3);
    setSelectedRegion4(r4);
  };

  const handleRegion3Change = (r3: string) => {
    const r4List = getRegion4List(selectedRegion1, selectedRegion2, r3);
    const r4 = r4List[0] || "";

    setSelectedRegion3(r3);
    setSelectedRegion4(r4);
  };

  useEffect(() => {
    const loadHotPlaces = async () => {
      const res = await apiFetchJson<HotPlace[]>("/api/v1/restaurants/today-hot");
      if (res.ok && res.data) {
        setHotPlaces(res.data);
      }
    };

    loadHotPlaces();
  }, []);

  useEffect(() => {
    let retryTimer: number | undefined;
    let cancelled = false;

    const initializeKakao = () => {
      if (cancelled) return;

      const maps = window.kakao?.maps;
      if (!maps) {
        retryTimer = window.setTimeout(initializeKakao, 100);
        return;
      }

      const createPlaces = () => {
        if (cancelled) return;
        if (!maps.services) {
          setRecommendError("카카오맵 services 라이브러리를 불러오지 못했습니다.");
          return;
        }
        placesRef.current = new maps.services.Places();
      };

      if (typeof maps.load === "function") {
        maps.load(createPlaces);
      } else {
        createPlaces();
      }
    };

    initializeKakao();

    return () => {
      cancelled = true;
      if (retryTimer) window.clearTimeout(retryTimer);
    };
  }, []);

  useEffect(() => {
    if (!resultModalOpen || !current || !mapRef.current || !window.kakao?.maps) return;

    const initMap = () => {
      if (!mapRef.current || !window.kakao?.maps?.LatLng || !window.kakao?.maps?.Map) return;

      const center = new window.kakao.maps.LatLng(current.lat, current.lng);
      const kakaoMap = new window.kakao.maps.Map(mapRef.current, {
        center,
        level: 3,
      }) as KakaoMap;
      setMap(kakaoMap);

      markerRef.current?.setMap(null);
      const marker = new window.kakao.maps.Marker({ position: center, map: kakaoMap }) as KakaoMarker;
      markerRef.current = marker;
    };

    if (typeof window.kakao.maps.load === "function") {
      window.kakao.maps.load(initMap);
    } else {
      initMap();
    }

    return () => {
      markerRef.current?.setMap(null);
      setMap(null);
    };
  }, [resultModalOpen, current]);

  const buildSearchKeyword = (): string => {
    const parts = [selectedRegion1];
    if (selectedRegion2 && selectedRegion2 !== "전체") parts.push(selectedRegion2);
    if (selectedRegion3 && selectedRegion3 !== "전체") parts.push(selectedRegion3);
    if (selectedRegion4 && selectedRegion4 !== "전체") parts.push(selectedRegion4);
    if (selectedCategory && selectedCategory !== "기타") {
      parts.push(selectedCategory === "아시안" ? "아시아음식" : selectedCategory);
    }
    return parts.join(" ");
  };

  // 카카오 키워드 검색을 최대 3페이지(45건)까지 수집
  const searchKakaoPool = (
    keyword: string,
    groupCode: string,
  ): Promise<KakaoRestaurant[]> => {
    return new Promise((resolve, reject) => {
      const maps = window.kakao?.maps;
      if (!maps || !placesRef.current || !maps.services) {
        reject(new Error("카카오맵 검색 서비스를 불러오는 중입니다."));
        return;
      }

      const services = maps.services;
      const all: KakaoRestaurant[] = [];

      placesRef.current.keywordSearch(
        keyword,
        (data: KakaoPlaceItem[], status: string, pagination: KakaoPagination) => {
          if (status === services.Status.OK) {
            all.push(
              ...data.map((item: KakaoPlaceItem) => {
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
                  region4: addressParts[3] || "",
                  phone: item.phone || "",
                  lat: Number(item.y),
                  lng: Number(item.x),
                };
              }),
            );
            if (pagination.hasNextPage && pagination.current < 3) {
              pagination.nextPage();
            } else {
              resolve(all);
            }
          } else if (status === services.Status.ZERO_RESULT) {
            resolve(all);
          } else {
            reject(new Error("검색 결과를 불러오지 못했습니다."));
          }
        },
        { category_group_code: groupCode, size: 15 },
      );
    });
  };

  const ensureRestaurant = async (
    restaurant: KakaoRestaurant,
  ): Promise<RecommendRestaurant | null> => {
    // 백엔드가 find-or-create를 처리하므로 바로 저장 요청
    const saveRes = await apiFetchJson<RecommendRestaurant>("/api/v1/restaurants", {
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
        region4: restaurant.region4,
        phone: restaurant.phone,
        lat: restaurant.lat,
        lng: restaurant.lng,
      }),
    });

    if (!saveRes.ok || !saveRes.data) {
      setRecommendError(saveRes.message || "식당 저장에 실패했습니다.");
      return null;
    }

    return saveRes.data;
  };

  const getCurrentPosition = (): Promise<{ lat: number; lng: number } | null> =>
    new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(null);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => resolve(null),
        // CoreLocation이 실패 콜백을 늦게 주는 환경에서 무한 대기하지 않도록
        { timeout: 5000, maximumAge: 60000 },
      );
    });

  const fetchRecommend = async () => {
    setRecommendLoading(true);
    setRecommendError("");
    setDistanceFallback(false);

    try {
      // 위치는 한 번만 요청 (거리순 폴 back 판단 + distanceMeter 계산에 공용)
      const position = await getCurrentPosition();

      let effectiveSort = sortBy;

      if (sortBy === "distance" && !position) {
        // 위치를 못 얻으면 에러 대신 랜덤으로 폴 back하고 안내 표시
        effectiveSort = "random";
        setDistanceFallback(true);
      }

      // 큐에 아직 안 본 후보가 남아있으면 재요청 없이 소비
      let queue = queueRef.current.filter((r) => !seenIdsRef.current.has(r.kakaoPlaceId));

      if (queue.length === 0) {
        const keyword = buildSearchKeyword();
        const groupCode = selectedCategory === "카페" ? "CE7" : "FD6";

        // mood 키워드를 포함해 검색하고, 결과가 없으면 mood 없이 재검색
        let pool = await searchKakaoPool(`${keyword} ${selectedMood}`.trim(), groupCode);
        if (pool.length === 0 && selectedMood) {
          pool = await searchKakaoPool(keyword, groupCode);
        }

        if (pool.length === 0) {
          setRecommendError("조건에 맞는 식당이 없습니다.");
          setCurrent(null);
          setRecommendLoading(false);
          return;
        }

        poolRef.current = pool;

        const res = await apiFetchJson<{ recommendations: RecommendItem[] }>(
          "/api/v1/restaurants/recommend",
          {
            method: "POST",
            body: JSON.stringify({
              candidates: pool.map((r) => ({
                kakaoPlaceId: r.kakaoPlaceId,
                name: r.name,
                categoryName: r.category,
                address: r.address,
                roadAddress: r.roadAddress,
                region1: r.region1,
                region2: r.region2,
                region3: r.region3,
                region4: r.region4,
                phone: r.phone,
                lat: r.lat,
                lng: r.lng,
              })),
              category: selectedCategory !== "기타" ? categoryToEnum[selectedCategory] : null,
              mood: MOOD_TAGS.find((t) => t.label === selectedMood)?.value ?? null,
              sort: effectiveSort === "distance" ? "DISTANCE" : "RANDOM",
              lat: position?.lat ?? null,
              lng: position?.lng ?? null,
              exclude: [...seenIdsRef.current],
            }),
          },
        );

        if (!res.ok || !res.data) {
          setRecommendError(res.message || "조건에 맞는 식당이 없습니다.");
          setCurrent(null);
          setRecommendLoading(false);
          return;
        }

        queueRef.current = res.data.recommendations;
        queue = queueRef.current.filter((r) => !seenIdsRef.current.has(r.kakaoPlaceId));

        if (queue.length === 0) {
          setRecommendError("조건에 맞는 식당이 없습니다.");
          setCurrent(null);
          setRecommendLoading(false);
          return;
        }
      }

      const next = queue[0];
      seenIdsRef.current.add(next.kakaoPlaceId);

      const full = poolRef.current.find((r) => r.kakaoPlaceId === next.kakaoPlaceId);
      if (!full) {
        setRecommendError("추천 정보를 찾지 못했습니다.");
        setCurrent(null);
        setRecommendLoading(false);
        return;
      }

      const saved = await ensureRestaurant(full);
      if (saved) {
        setCurrentLabel(next.categoryLabel);
        setCurrent(saved);
      } else {
        setCurrent(null);
      }
    } catch (error) {
      setRecommendError(error instanceof Error ? error.message : "추천을 불러오지 못했습니다.");
      setCurrent(null);
    }

    setRecommendLoading(false);
  };

  const handleRecommend = async () => {
    poolRef.current = [];
    queueRef.current = [];
    seenIdsRef.current = new Set();
    await fetchRecommend();
    setResultModalOpen(true);
  };

  const handleNext = async () => {
    await fetchRecommend();
  };

  const handleDecide = () => {
    if (!current) return;
    setResultModalOpen(false);
    router.push(`/restaurant/${current.id}`);
  };

  const handleSave = () => {
    alert("리스트에 저장되었습니다. (API 연동 예정)");
    setResultModalOpen(false);
  };

  return (
    <AppShell
      leftSidebar={
        <div className="sticky top-28 space-y-5">
          <SidebarProfile />
          <SidebarCard title="오늘의 핫플">
            <HotPlaceList hotPlaces={hotPlaces} />
          </SidebarCard>
        </div>
      }
    >
      <div className="space-y-5">
        <div>
          <h2 className="text-xl font-bold text-ink">맛집 추천</h2>
          <p className="mt-1 text-sm text-muted">조건을 선택하면 오늘의 밥집을 추천해드려요</p>
        </div>

        <div className="rounded-2xl bg-surface p-6 border border-hairline-soft shadow-sm space-y-6">
          {/* Location */}
          <div>
            <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
              <MapPin className="h-4 w-4 text-primary" />
              위치
            </div>
            <button
              onClick={() => setLocationModalOpen(true)}
              className="flex w-full items-center justify-between rounded-xl border border-hairline bg-surface-soft px-4 py-3 text-left text-sm text-ink hover:bg-white transition-colors"
            >
              <span>{locationLabel || "위치 선택"}</span>
              <ChevronRight className="h-4 w-4 text-muted" />
            </button>
          </div>

          {/* Category */}
          <div>
            <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
              <Utensils className="h-4 w-4 text-primary" />
              종류
            </div>
            <div className="flex flex-wrap gap-2">
              {categories.map((item) => (
                <button
                  key={item}
                  onClick={() => setSelectedCategory(item)}
                  className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    selectedCategory === item
                      ? "bg-primary text-white"
                      : "bg-surface-soft text-muted hover:bg-hairline-soft"
                  }`}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>

          {/* Mood */}
          <div>
            <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
              <Sparkles className="h-4 w-4 text-primary" />
              분위기
            </div>
            <div className="flex flex-wrap gap-2">
              {moods.map((item) => (
                <button
                  key={item}
                  onClick={() => setSelectedMood(item)}
                  className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    selectedMood === item
                      ? "bg-primary text-white"
                      : "bg-surface-soft text-muted hover:bg-hairline-soft"
                  }`}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-hairline-soft pt-5">
            <div className="flex gap-2">
              {sorts.map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setSortBy(key)}
                  className={`flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    sortBy === key ? "bg-primary text-white" : "bg-surface-soft text-muted hover:bg-hairline-soft"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {label}
                </button>
              ))}
            </div>
            <button
              onClick={handleRecommend}
              disabled={recommendLoading}
              className="rounded-xl bg-primary px-6 py-2.5 text-sm font-bold text-white hover:bg-primary-active transition-colors disabled:opacity-70"
            >
              {recommendLoading ? "추천 중..." : "추천 받기"}
            </button>
          </div>
        </div>

        {/* 사이드바 콘텐츠 흡수 (xl 미만) */}
        {hotPlaces.length > 0 && (
          <div className="rounded-2xl border border-hairline-soft bg-surface p-6 shadow-sm xl:hidden">
            <p className="mb-4 text-base font-bold text-ink">오늘의 핫플</p>

            <HotPlaceList hotPlaces={hotPlaces} />
          </div>
        )}
      </div>

      {/* Location modal */}
      {locationModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-xs">
          <div className="max-h-[calc(100vh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl bg-surface p-5 shadow-xl animate-in fade-in-50 zoom-in-95">
            <div className="flex items-center justify-between border-b border-hairline-soft pb-3">
              <h3 className="text-base font-bold text-ink">위치 선택</h3>
              <button onClick={() => setLocationModalOpen(false)} className="text-muted hover:text-ink">
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 grid h-72 grid-cols-4 gap-1.5 sm:gap-2">
              <div className="overflow-y-auto rounded-lg border border-hairline-soft bg-surface-soft">
                {region1List.map((r1) => (
                  <button
                    key={r1}
                    onClick={() => handleRegion1Change(r1)}
                    className={`block w-full px-2 py-2 text-left text-xs sm:px-3 sm:text-sm ${
                      selectedRegion1 === r1 ? "bg-primary text-white" : "text-ink hover:bg-white"
                    }`}
                  >
                    {r1}
                  </button>
                ))}
              </div>

              <div className="overflow-y-auto rounded-lg border border-hairline-soft bg-surface-soft">
                {getRegion2List(selectedRegion1).map((r2) => (
                  <button
                    key={r2}
                    onClick={() => handleRegion2Change(r2)}
                    className={`block w-full px-2 py-2 text-left text-xs sm:px-3 sm:text-sm ${
                      selectedRegion2 === r2 ? "bg-primary text-white" : "text-ink hover:bg-white"
                    }`}
                  >
                    {r2}
                  </button>
                ))}
              </div>

              <div className="overflow-y-auto rounded-lg border border-hairline-soft bg-surface-soft">
                {getRegion3List(selectedRegion1, selectedRegion2).map((r3) => (
                  <button
                    key={r3}
                    onClick={() => handleRegion3Change(r3)}
                    className={`block w-full px-2 py-2 text-left text-xs sm:px-3 sm:text-sm ${
                      selectedRegion3 === r3 ? "bg-primary text-white" : "text-ink hover:bg-white"
                    }`}
                  >
                    {r3}
                  </button>
                ))}
              </div>

              <div className="overflow-y-auto rounded-lg border border-hairline-soft bg-surface-soft">
                {getRegion4List(selectedRegion1, selectedRegion2, selectedRegion3).map((r4) => (
                  <button
                    key={r4}
                    onClick={() => setSelectedRegion4(r4)}
                    className={`block w-full px-2 py-2 text-left text-xs sm:px-3 sm:text-sm ${
                      selectedRegion4 === r4 ? "bg-primary text-white" : "text-ink hover:bg-white"
                    }`}
                  >
                    {r4}
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={() => setLocationModalOpen(false)}
              className="mt-4 w-full rounded-lg bg-primary py-2.5 text-sm font-bold text-white hover:bg-primary-active"
            >
              선택 완료
            </button>
          </div>
        </div>
      )}

      {/* Result modal */}
      {resultModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
          <div className="recommend-result-modal w-full max-w-lg max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-3xl bg-surface p-5 shadow-2xl animate-in fade-in-50 zoom-in-95 sm:p-8">
            <div className="recommend-result-header relative mb-6 flex items-center justify-center border-b border-hairline-soft pb-3">
              <div className="text-center">
                <p className="text-sm font-bold text-primary mb-1">오늘의 추천</p>
                <h3 className="text-xl font-bold text-ink">이런 곳은 어때요?</h3>
              </div>
              <button
                onClick={() => setResultModalOpen(false)}
                className="absolute right-0 top-0 text-muted hover:text-ink"
                aria-label="닫기"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {recommendError ? (
              <div className="flex flex-col items-center py-12 text-center">
                <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-surface-soft">
                  <SearchX className="h-8 w-8 text-muted" />
                </div>
                <p className="text-base font-bold text-ink">조건에 맞는 식당이 없습니다</p>
                <p className="mt-1 text-sm text-muted">{recommendError}</p>
                <div className="mt-6 flex gap-3">
                  <button
                    onClick={() => setResultModalOpen(false)}
                    className="flex items-center gap-1.5 rounded-xl border border-hairline bg-surface px-5 py-2.5 text-sm font-bold text-ink hover:bg-surface-soft transition-colors"
                  >
                    <ArrowLeft className="h-4 w-4" />
                    다시 추천받기
                  </button>
                  <button
                    onClick={() => {
                      setResultModalOpen(false);
                      router.push("/recommend");
                    }}
                    className="flex items-center gap-1.5 rounded-xl bg-primary px-5 py-2.5 text-sm font-bold text-white hover:bg-primary-active transition-colors"
                  >
                    <Home className="h-4 w-4" />
                    홈으로 가기
                  </button>
                </div>
              </div>
            ) : !current ? (
              <div className="flex items-center justify-center py-10">
                <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              </div>
            ) : (
              <>
                {distanceFallback && (
                  <p className="mb-3 rounded-xl bg-surface-soft px-4 py-2.5 text-center text-xs font-bold text-muted">
                    현재 위치를 가져올 수 없어 랜덤으로 추천했어요
                  </p>
                )}

                <div className="recommend-result-main">
                  {/* Map */}
                  <div className="recommend-result-map relative mb-4 h-48 w-full overflow-hidden rounded-2xl border border-hairline-soft sm:h-56">
                    <div ref={mapRef} className="absolute inset-0 bg-surface-strong" />
                    <button
                      onClick={() => {
                        const maps = window.kakao?.maps;
                        if (!map || !navigator.geolocation || !maps) return;
                        navigator.geolocation.getCurrentPosition(
                          (pos) => {
                            const center = new maps.LatLng(
                              pos.coords.latitude,
                              pos.coords.longitude
                            );
                            map.setCenter(center);
                          },
                          () => alert("현재 위치를 가져올 수 없습니다.")
                        );
                      }}
                      className="absolute bottom-2 right-2 flex items-center justify-center rounded-lg border border-hairline bg-surface/90 p-1.5 text-muted shadow-sm hover:bg-white"
                      aria-label="현재 위치"
                    >
                      <Navigation className="h-4 w-4" />
                    </button>
                  </div>

                  {/* Draft card */}
                  <div className="recommend-result-card rounded-2xl bg-surface border border-hairline-soft overflow-hidden shadow-sm">
                    <div className="recommend-result-category-banner flex h-12 w-full items-center justify-center bg-primary-soft text-3xl sm:h-14">
                      {categoryEmoji[currentLabel] || "🍽\uFE0F"}
                    </div>
                    <div className="recommend-result-card-content p-4 sm:p-6">
                      <div className="flex items-center gap-2 mb-2">
                        <span className="rounded-full bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-active">
                          {currentLabel}
                        </span>
                        <span className="rounded-full bg-tag-mood px-2.5 py-1 text-xs font-bold text-ink">
                          {selectedMood}
                        </span>
                      </div>
                      <h4 className="text-2xl font-bold text-ink">{current.name}</h4>
                      <p className="mt-1 text-sm text-muted">
                        {current.region1} {current.region2} {current.region3} {current.region4}
                      </p>

                      <div className="mt-4 space-y-1.5 text-sm text-body">
                        <p>{current.roadAddress}</p>
                        <p className="text-muted-soft">{current.address}</p>
                        <p>전화: {current.phone}</p>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Action buttons */}
                <div className="recommend-result-actions mt-6 grid grid-cols-[1.25fr_1fr_1fr] gap-3">
                  <button
                    onClick={handleDecide}
                    className="flex items-center justify-center gap-1.5 rounded-xl bg-primary py-3 text-sm font-bold text-white hover:bg-primary-active transition-colors"
                  >
                    <Check className="h-4 w-4" />
                    여기로 결정
                  </button>
                  <button
                    onClick={handleNext}
                    disabled={recommendLoading}
                    className="recommend-secondary-action flex items-center justify-center gap-1.5 whitespace-nowrap rounded-xl border border-hairline bg-surface py-3 text-sm font-bold text-muted hover:bg-surface-soft transition-colors disabled:opacity-70"
                  >
                    <RotateCcw className="h-4 w-4" />
                    다른곳 추천
                  </button>
                  <button
                    onClick={handleSave}
                    className="recommend-secondary-action flex items-center justify-center gap-1.5 whitespace-nowrap rounded-xl border border-hairline bg-surface py-3 text-sm font-bold text-muted hover:bg-surface-soft transition-colors"
                  >
                    <Bookmark className="h-4 w-4" />
                    리스트 저장
                  </button>
                </div>

                <button
                  onClick={() => setResultModalOpen(false)}
                  className="recommend-result-close mt-3 flex w-full items-center justify-center gap-1.5 rounded-xl border border-hairline bg-surface-soft py-3 text-sm font-bold text-muted hover:bg-surface transition-colors"
                >
                  <ArrowLeft className="h-4 w-4" />
                  추천 페이지로 돌아가기
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </AppShell>
  );
}
