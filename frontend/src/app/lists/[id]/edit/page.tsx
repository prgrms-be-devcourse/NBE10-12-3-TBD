"use client";

import { FormEvent, useEffect, useState } from "react";

import { useParams, useRouter } from "next/navigation";

import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Loader2,
  Plus,
  Search,
  Trash2,
  X,
} from "lucide-react";

import AppShell, { SidebarCard, SidebarProfile } from "@/components/AppShell";

import { apiFetchJson } from "@/lib/api";

/* =========================================================
 * Kakao 검색 결과 원본
 * ========================================================= */

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

/* =========================================================
 * Kakao 검색 결과 화면용
 * ========================================================= */

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

/* =========================================================
 * 리스트 상세 응답 아이템
 * ========================================================= */

interface ListItem {
  id: number;
  listId: number;
  restaurantId: number;
  restaurantName: string;
  category: string;
  orderIndex: number;
  memo: string;
  createdAt: string;
}

/* =========================================================
 * 수정 화면에서 사용할 아이템
 * ========================================================= */

interface EditableListItem {
  id?: number;

  listId?: number;

  restaurantId: number;

  restaurantName: string;

  category: string;

  orderIndex: number;

  memo: string;

  isNew: boolean;
}

/* =========================================================
 * 리스트 상세 응답
 * ========================================================= */

interface ListDetail {
  listId: number;

  userId: number;

  nickname: string;

  title: string;

  description: string;

  moodTag: string;

  items: ListItem[];

  createdAt: string;
}

/* =========================================================
 * Restaurant 조회 / 생성 응답
 * ========================================================= */

interface RestaurantResponse {
  id: number;
}

/* =========================================================
 * 분위기
 * ========================================================= */

const moodTags = ["SOLO", "DATE", "FAMILY", "HEALING"];

/* =========================================================
 * 음식점 / 카페만 남김
 * ========================================================= */

function isFoodOrCafe(item: KakaoPlaceItem): boolean {
  return (
    item.category_name.startsWith("음식점") ||
    item.category_name.startsWith("카페")
  );
}

/* =========================================================
 * Kakao 결과 변환
 * ========================================================= */

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

/* =========================================================
 * 카테고리 표시
 * ========================================================= */

function getDisplayCategory(category: string): string {
  const parts = category
    .split(">")
    .map((part) => part.trim())
    .filter(Boolean);

  if (parts.length >= 3) {
    return `${parts[1]} · ${parts[parts.length - 1]}`;
  }

  if (parts.length === 2) {
    return parts[1];
  }

  return parts.join(" · ") || category || "기타";
}

/* =========================================================
 * 페이지
 * ========================================================= */

export default function ListEditPage() {
  const router = useRouter();

  const params = useParams<{
    id: string;
  }>();

  const listId = Number(params.id);

  const kakaoKey = process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

  /* =======================================================
   * 리스트 기본 정보
   * ======================================================= */

  const [title, setTitle] = useState("");

  const [description, setDescription] = useState("");

  const [moodTag, setMoodTag] = useState("SOLO");

  /* =======================================================
   * 현재 편집 중인 리스트 아이템
   * ======================================================= */

  const [items, setItems] = useState<EditableListItem[]>([]);

  /* =======================================================
   * 삭제 예정인 기존 아이템
   * ======================================================= */

  const [removedItemIds, setRemovedItemIds] = useState<number[]>([]);

  /* =======================================================
   * 검색 UI
   * ======================================================= */

  const [showRestaurantSearch, setShowRestaurantSearch] = useState(false);

  const [restaurantQuery, setRestaurantQuery] = useState("");

  const [restaurantResults, setRestaurantResults] = useState<KakaoRestaurant[]>(
    [],
  );

  const [restaurantSearching, setRestaurantSearching] = useState(false);

  const [restaurantSearchError, setRestaurantSearchError] = useState(() => {
    if (!kakaoKey) {
      return "카카오맵 JS 키가 설정되지 않았습니다.";
    }

    return "";
  });

  /* =======================================================
   * Kakao 장소 ID → DB Restaurant ID
   * ======================================================= */

  const [restaurantIdByPlaceId, setRestaurantIdByPlaceId] = useState<
    Record<string, number>
  >({});

  /* =======================================================
   * 현재 추가 처리 중인 장소
   * ======================================================= */

  const [addingPlaceIds, setAddingPlaceIds] = useState<string[]>([]);

  /* =======================================================
   * Kakao SDK 상태
   * ======================================================= */

  const [kakaoReady, setKakaoReady] = useState(false);

  /* =======================================================
   * 화면 상태
   * ======================================================= */

  const [loading, setLoading] = useState(() => {
    if (!Number.isFinite(listId) || listId <= 0) {
      return false;
    }

    return true;
  });

  const [saving, setSaving] = useState(false);

  const [error, setError] = useState(() => {
    if (!Number.isFinite(listId) || listId <= 0) {
      return "올바르지 않은 리스트 번호입니다.";
    }

    return "";
  });

  /* =======================================================
   * 알림창
   * ======================================================= */

  const [alertOpen, setAlertOpen] = useState(false);

  const [alertMessage, setAlertMessage] = useState("");

  const [moveAfterAlert, setMoveAfterAlert] = useState(false);

  /* =========================================================
   * 알림창 열기
   * ========================================================= */

  const showAlert = (message: string) => {
    setAlertMessage(message);

    setAlertOpen(true);
  };

  /* =========================================================
   * 알림창 닫기
   *
   * 수정 성공 후에는 리스트 페이지로 이동
   * ========================================================= */

  const handleAlertClose = () => {
    setAlertOpen(false);

    if (moveAfterAlert) {
      setMoveAfterAlert(false);

      router.push(`/lists?listId=${listId}`);
    }
  };

  /* =========================================================
   * 리스트 상세 조회
   * ========================================================= */

  useEffect(() => {
    if (!Number.isFinite(listId) || listId <= 0) {
      return;
    }

    const loadList = async () => {
      setLoading(true);

      setError("");

      try {
        const res = await apiFetchJson<ListDetail>(`/api/v1/lists/${listId}`);

        console.log("리스트 상세 조회:", res);

        if (!res.ok || !res.data) {
          setError(res.message || "리스트를 불러오지 못했습니다.");

          return;
        }

        setTitle(res.data.title ?? "");

        setDescription(res.data.description ?? "");

        setMoodTag(res.data.moodTag ?? "SOLO");

        const editableItems = [...(res.data.items ?? [])]
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map(
            (item): EditableListItem => ({
              id: item.id,

              listId: item.listId,

              restaurantId: item.restaurantId,

              restaurantName: item.restaurantName,

              category: item.category,

              orderIndex: item.orderIndex,

              memo: item.memo ?? "",

              isNew: false,
            }),
          );

        setItems(editableItems);
      } catch (requestError) {
        console.error("리스트 상세 조회 실패:", requestError);

        setError("리스트를 불러오는 중 오류가 발생했습니다.");
      } finally {
        setLoading(false);
      }
    };

    void loadList();
  }, [listId]);

  /* =========================================================
   * Kakao SDK 로딩
   * ========================================================= */

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const loadKakaoServices = () => {
      if (!window.kakao?.maps) {
        return;
      }

      window.kakao.maps.load(() => {
        if (window.kakao?.maps?.services) {
          setKakaoReady(true);
        }
      });
    };

    if (window.kakao?.maps) {
      loadKakaoServices();

      return;
    }

    const existing = document.getElementById(
      "kakao-map-sdk",
    ) as HTMLScriptElement | null;

    if (existing) {
      existing.addEventListener("load", loadKakaoServices);

      const check = setInterval(() => {
        if (window.kakao?.maps) {
          clearInterval(check);

          loadKakaoServices();
        }
      }, 100);

      return () => {
        clearInterval(check);

        existing.removeEventListener("load", loadKakaoServices);
      };
    }

    if (!kakaoKey) {
      return;
    }

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

    script.onload = loadKakaoServices;

    script.onerror = () => {
      setRestaurantSearchError("카카오맵 SDK를 불러오지 못했습니다.");
    };

    document.head.appendChild(script);
  }, [kakaoKey]);

  /* =========================================================
   * 검색 결과의 장소들을 DB Restaurant과 연결
   * ========================================================= */

  const resolveRestaurantIds = async (restaurants: KakaoRestaurant[]) => {
    const entries = await Promise.all(
      restaurants.map(async (restaurant) => {
        try {
          const res = await apiFetchJson<RestaurantResponse>(
            `/api/v1/restaurants?kakaoPlaceId=${restaurant.kakaoPlaceId}`,
          );

          if (res.ok && res.data) {
            return [restaurant.kakaoPlaceId, res.data.id] as const;
          }
        } catch (requestError) {
          console.log("Restaurant 확인 실패:", restaurant.name, requestError);
        }

        return null;
      }),
    );

    const nextMap: Record<string, number> = {};

    entries.forEach((entry) => {
      if (!entry) {
        return;
      }

      const [kakaoPlaceId, restaurantId] = entry;

      nextMap[kakaoPlaceId] = restaurantId;
    });

    setRestaurantIdByPlaceId(nextMap);
  };

  /* =========================================================
   * 검색 영역 열기
   * ========================================================= */

  const handleOpenRestaurantSearch = () => {
    setShowRestaurantSearch(true);

    setRestaurantSearchError("");
  };

  /* =========================================================
   * Kakao 키워드 검색
   * ========================================================= */

  const handleRestaurantSearch = () => {
    const trimmedQuery = restaurantQuery.trim();

    if (!trimmedQuery) {
      setRestaurantSearchError("검색어를 입력해주세요.");

      return;
    }

    setRestaurantSearching(true);

    setRestaurantSearchError("");

    const maps = window.kakao?.maps;

    const services = maps?.services;

    if (!maps || !services) {
      setRestaurantSearchError("카카오맵 SDK를 불러오지 못했습니다.");

      setRestaurantSearching(false);

      return;
    }

    const places = new services.Places();

    places.keywordSearch(
      trimmedQuery,

      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          const foodAndCafe = data.filter(isFoodOrCafe);

          if (foodAndCafe.length === 0) {
            setRestaurantResults([]);

            setRestaurantIdByPlaceId({});

            setRestaurantSearchError("검색된 음식점이나 카페가 없습니다.");

            setRestaurantSearching(false);

            return;
          }

          const mapped = mapKakaoPlaces(foodAndCafe);

          setRestaurantResults(mapped);

          setRestaurantSearchError("");

          void resolveRestaurantIds(mapped);
        } else if (status === services.Status.ZERO_RESULT) {
          setRestaurantResults([]);

          setRestaurantIdByPlaceId({});

          setRestaurantSearchError("검색 결과가 없습니다.");
        } else {
          setRestaurantResults([]);

          setRestaurantIdByPlaceId({});

          setRestaurantSearchError("검색 결과를 불러오지 못했습니다.");
        }

        setRestaurantSearching(false);
      },

      {
        size: 15,
      },
    );
  };

  /* =========================================================
   * 검색 Enter
   * ========================================================= */

  const handleRestaurantSearchSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    handleRestaurantSearch();
  };

  /* =========================================================
   * 이미 현재 리스트에 있는지 확인
   * ========================================================= */

  const isAlreadyAdded = (kakaoPlaceId: string): boolean => {
    const restaurantId = restaurantIdByPlaceId[kakaoPlaceId];

    if (!restaurantId) {
      return false;
    }

    return items.some((item) => item.restaurantId === restaurantId);
  };

  /* =========================================================
   * 추가 중인지 확인
   * ========================================================= */

  const isAddingPlace = (kakaoPlaceId: string): boolean => {
    return addingPlaceIds.includes(kakaoPlaceId);
  };

  /* =========================================================
   * 장소 추가
   * ========================================================= */

  const handleAddRestaurant = async (restaurant: KakaoRestaurant) => {
    if (isAlreadyAdded(restaurant.kakaoPlaceId)) {
      return;
    }

    if (isAddingPlace(restaurant.kakaoPlaceId)) {
      return;
    }

    setAddingPlaceIds((prev) => [...prev, restaurant.kakaoPlaceId]);

    try {
      let restaurantId = restaurantIdByPlaceId[restaurant.kakaoPlaceId];

      if (!restaurantId) {
        const saveRes = await apiFetchJson<RestaurantResponse>(
          "/api/v1/restaurants",
          {
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
          },
        );

        if (!saveRes.ok || !saveRes.data) {
          showAlert(saveRes.message || "장소 저장에 실패했습니다.");

          return;
        }

        restaurantId = saveRes.data.id;

        setRestaurantIdByPlaceId((prev) => ({
          ...prev,

          [restaurant.kakaoPlaceId]: restaurantId,
        }));
      }

      const alreadyExists = items.some(
        (item) => item.restaurantId === restaurantId,
      );

      if (alreadyExists) {
        return;
      }

      setItems((prev) => [
        ...prev,

        {
          restaurantId,

          restaurantName: restaurant.name,

          category: restaurant.category,

          orderIndex: prev.length + 1,

          memo: "",

          isNew: true,
        },
      ]);
    } catch (addError) {
      console.error("장소 추가 실패:", addError);

      showAlert("장소를 추가하는 중 오류가 발생했습니다.");
    } finally {
      setAddingPlaceIds((prev) =>
        prev.filter((placeId) => placeId !== restaurant.kakaoPlaceId),
      );
    }
  };

  /* =========================================================
   * 메모 변경
   * ========================================================= */

  const handleMemoChange = (index: number, memo: string) => {
    setItems((prev) =>
      prev.map((item, itemIndex) =>
        itemIndex === index
          ? {
              ...item,

              memo,
            }
          : item,
      ),
    );
  };

  /* =========================================================
   * 아이템 제거
   * ========================================================= */

  const handleRemoveItem = (index: number) => {
    const target = items[index];

    if (!target) {
      return;
    }

    if (!target.isNew && target.id) {
      setRemovedItemIds((prev) =>
        prev.includes(target.id!) ? prev : [...prev, target.id!],
      );
    }

    setItems((prev) =>
      prev
        .filter((_, itemIndex) => itemIndex !== index)
        .map((item, itemIndex) => ({
          ...item,

          orderIndex: itemIndex + 1,
        })),
    );
  };

  /* =========================================================
   * 위로 이동
   * ========================================================= */

  const handleMoveUp = (index: number) => {
    if (index === 0) {
      return;
    }

    setItems((prev) => {
      const next = [...prev];

      [next[index - 1], next[index]] = [next[index], next[index - 1]];

      return next.map((item, itemIndex) => ({
        ...item,

        orderIndex: itemIndex + 1,
      }));
    });
  };

  /* =========================================================
   * 아래로 이동
   * ========================================================= */

  const handleMoveDown = (index: number) => {
    if (index === items.length - 1) {
      return;
    }

    setItems((prev) => {
      const next = [...prev];

      [next[index], next[index + 1]] = [next[index + 1], next[index]];

      return next.map((item, itemIndex) => ({
        ...item,

        orderIndex: itemIndex + 1,
      }));
    });
  };

  /* =========================================================
   * 수정 완료
   * ========================================================= */

  const handleSave = async () => {
    if (!title.trim()) {
      showAlert("리스트 제목을 입력해주세요.");

      return;
    }

    if (items.length === 0) {
      showAlert("리스트에는 식당이 한 개 이상 필요합니다.");

      return;
    }

    setSaving(true);

    try {
      /* -------------------------------------------------
       * 1. 리스트 기본 정보 수정
       * ------------------------------------------------- */

      const listRes = await apiFetchJson(`/api/v1/lists/${listId}`, {
        method: "PUT",

        body: JSON.stringify({
          title: title.trim(),

          description: description.trim(),

          moodTag,
        }),
      });

      if (!listRes.ok) {
        showAlert(listRes.message || "리스트 정보 수정에 실패했습니다.");

        return;
      }

      /* -------------------------------------------------
       * 2. 삭제한 기존 아이템
       * ------------------------------------------------- */

      for (const itemId of removedItemIds) {
        const deleteRes = await apiFetchJson(
          `/api/v1/lists/${listId}/items/${itemId}`,
          {
            method: "DELETE",
          },
        );

        if (!deleteRes.ok) {
          showAlert(deleteRes.message || "식당 삭제에 실패했습니다.");

          return;
        }
      }

      /* -------------------------------------------------
       * 3. 새 아이템 추가 / 기존 아이템 수정
       * ------------------------------------------------- */

      for (const item of items) {
        if (item.isNew) {
          const addRes = await apiFetchJson(`/api/v1/lists/${listId}/items`, {
            method: "POST",

            body: JSON.stringify({
              restaurantId: item.restaurantId,

              memo: item.memo,

              orderIndex: item.orderIndex,
            }),
          });

          if (!addRes.ok) {
            showAlert(
              addRes.message || `${item.restaurantName} 추가에 실패했습니다.`,
            );

            return;
          }

          continue;
        }

        if (!item.id) {
          continue;
        }

        const updateRes = await apiFetchJson(
          `/api/v1/lists/${listId}/items/${item.id}`,
          {
            method: "PUT",

            body: JSON.stringify({
              restaurantId: item.restaurantId,

              orderIndex: item.orderIndex,

              memo: item.memo,
            }),
          },
        );

        if (!updateRes.ok) {
          showAlert(
            updateRes.message || `${item.restaurantName} 수정에 실패했습니다.`,
          );

          return;
        }
      }

      setMoveAfterAlert(true);

      showAlert("리스트를 수정했습니다.");
    } catch (saveError) {
      console.error("리스트 수정 실패:", saveError);

      showAlert("리스트 수정 중 오류가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  };

  /* =========================================================
   * 로딩
   * ========================================================= */

  if (loading) {
    return (
      <AppShell
        leftSidebar={
          <div className="sticky top-28">
            <SidebarProfile />
          </div>
        }
      >
        <div className="py-20 text-center text-sm text-muted">
          리스트를 불러오는 중입니다.
        </div>
      </AppShell>
    );
  }

  /* =========================================================
   * 오류
   * ========================================================= */

  if (error) {
    return (
      <AppShell
        leftSidebar={
          <div className="sticky top-28">
            <SidebarProfile />
          </div>
        }
      >
        <div className="py-20 text-center text-sm text-red-500">{error}</div>
      </AppShell>
    );
  }

  /* =========================================================
   * 화면
   * ========================================================= */

  return (
    <>
      <AppShell
        leftSidebar={
          <div className="sticky top-28 space-y-5">
            <SidebarProfile />

            <SidebarCard title="리스트 수정">
              <p className="text-sm leading-6 text-muted">
                리스트 정보와 식당, 순서와 메모를 수정할 수 있습니다.
              </p>
            </SidebarCard>
          </div>
        }
      >
        <div className="mx-auto max-w-3xl">
          {/* 상단 */}

          <div className="mb-6 flex items-center gap-3">
            <button
              type="button"
              onClick={() => router.back()}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-hairline bg-white hover:bg-surface-soft"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>

            <div>
              <h1 className="text-2xl font-bold text-ink">리스트 수정</h1>

              <p className="mt-1 text-sm text-muted">
                리스트 정보와 식당을 수정하세요.
              </p>
            </div>
          </div>

          <div className="space-y-6">
            {/* 리스트 기본 정보 */}

            <section className="rounded-2xl border border-hairline-soft bg-white p-6">
              <h2 className="mb-5 text-lg font-bold text-ink">리스트 정보</h2>

              <div className="space-y-5">
                <div>
                  <label className="mb-2 block text-sm font-bold text-ink">
                    리스트 제목
                  </label>

                  <input
                    type="text"
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-3 text-sm outline-none focus:border-primary"
                  />
                </div>

                <div>
                  <label className="mb-2 block text-sm font-bold text-ink">
                    설명
                  </label>

                  <textarea
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    rows={4}
                    className="w-full resize-none rounded-xl border border-hairline bg-surface-soft px-4 py-3 text-sm outline-none focus:border-primary"
                  />
                </div>

                <div>
                  <label className="mb-2 block text-sm font-bold text-ink">
                    분위기
                  </label>

                  <select
                    value={moodTag}
                    onChange={(event) => setMoodTag(event.target.value)}
                    className="w-full rounded-xl border border-hairline bg-surface-soft px-4 py-3 text-sm outline-none focus:border-primary"
                  >
                    {moodTags.map((tag) => (
                      <option key={tag} value={tag}>
                        {tag}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </section>

            {/* 식당 목록 */}

            <section className="rounded-2xl border border-hairline-soft bg-white p-6">
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-ink">식당 목록</h2>

                  <p className="mt-1 text-sm text-muted">{items.length}개</p>
                </div>

                <button
                  type="button"
                  onClick={handleOpenRestaurantSearch}
                  className="flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2.5 text-sm font-bold text-white hover:bg-primary-active"
                >
                  <Plus className="h-4 w-4" />
                  식당 추가
                </button>
              </div>

              {/* 검색 영역 */}

              {showRestaurantSearch && (
                <div className="mb-6 rounded-2xl border border-primary/20 bg-primary/5 p-4">
                  <div className="mb-4 flex items-start justify-between">
                    <div>
                      <h3 className="font-bold text-ink">식당·카페 검색</h3>

                      <p className="mt-1 text-xs text-muted">
                        음식점, 카페, 디저트를 검색해서 추가하세요.
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        setShowRestaurantSearch(false);

                        setRestaurantQuery("");

                        setRestaurantResults([]);

                        setRestaurantIdByPlaceId({});

                        setRestaurantSearchError("");
                      }}
                      className="flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-white"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>

                  <form
                    onSubmit={handleRestaurantSearchSubmit}
                    className="flex gap-2"
                  >
                    <div className="flex min-w-0 flex-1 items-center gap-2 rounded-xl border border-hairline bg-white px-4 py-3">
                      <Search className="h-4 w-4 shrink-0 text-muted" />

                      <input
                        type="text"
                        value={restaurantQuery}
                        onChange={(event) =>
                          setRestaurantQuery(event.target.value)
                        }
                        placeholder="예: 서면 초밥, 전포 카페"
                        className="min-w-0 flex-1 bg-transparent text-sm outline-none"
                        autoFocus
                      />
                    </div>

                    <button
                      type="submit"
                      disabled={restaurantSearching || !kakaoReady}
                      className="rounded-xl bg-primary px-4 py-3 text-sm font-bold text-white disabled:opacity-50"
                    >
                      {restaurantSearching ? "검색 중..." : "검색"}
                    </button>
                  </form>

                  {!kakaoReady && !restaurantSearchError && (
                    <div className="mt-4 flex items-center justify-center gap-2 py-4 text-sm text-muted">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      카카오 검색 서비스를 불러오는 중입니다.
                    </div>
                  )}

                  {restaurantSearchError && (
                    <p className="mt-4 text-center text-sm text-red-500">
                      {restaurantSearchError}
                    </p>
                  )}

                  {restaurantResults.length > 0 && (
                    <div className="mt-4">
                      <p className="mb-3 text-sm font-bold text-ink">
                        검색 결과{" "}
                        <span className="text-primary">
                          {restaurantResults.length}개
                        </span>
                      </p>

                      <div className="max-h-96 space-y-2 overflow-y-auto">
                        {restaurantResults.map((restaurant) => {
                          const alreadyAdded = isAlreadyAdded(
                            restaurant.kakaoPlaceId,
                          );

                          const adding = isAddingPlace(restaurant.kakaoPlaceId);

                          return (
                            <div
                              key={restaurant.kakaoPlaceId}
                              className={`flex items-center justify-between gap-4 rounded-xl border p-4 ${
                                alreadyAdded
                                  ? "border-primary/20 bg-primary/5"
                                  : "border-hairline-soft bg-white"
                              }`}
                            >
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                  <p className="truncate font-bold text-ink">
                                    {restaurant.name}
                                  </p>

                                  {alreadyAdded && (
                                    <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">
                                      리스트에 있음
                                    </span>
                                  )}
                                </div>

                                <p className="mt-1 text-xs text-muted">
                                  {getDisplayCategory(restaurant.category)}
                                </p>

                                <p className="mt-1 line-clamp-1 text-xs text-body">
                                  {restaurant.roadAddress || restaurant.address}
                                </p>
                              </div>

                              <button
                                type="button"
                                disabled={alreadyAdded || adding}
                                onClick={() => handleAddRestaurant(restaurant)}
                                className={`min-w-16 rounded-lg px-3 py-2 text-xs font-bold ${
                                  alreadyAdded
                                    ? "cursor-not-allowed bg-surface-strong text-muted"
                                    : adding
                                      ? "bg-primary/50 text-white"
                                      : "bg-primary text-white hover:bg-primary-active"
                                }`}
                              >
                                {alreadyAdded
                                  ? "추가됨"
                                  : adding
                                    ? "추가 중"
                                    : "추가"}
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* 현재 식당 목록 */}

              <div className="space-y-4">
                {items.map((item, index) => (
                  <div
                    key={item.id ?? `new-${item.restaurantId}`}
                    className="rounded-2xl bg-surface-soft p-4"
                  >
                    <div className="flex gap-4">
                      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
                        {index + 1}
                      </span>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <div className="flex items-center gap-2">
                              <h3 className="font-bold text-ink">
                                {item.restaurantName}
                              </h3>

                              {item.isNew && (
                                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">
                                  새로 추가
                                </span>
                              )}
                            </div>

                            <p className="mt-1 text-xs text-muted">
                              {getDisplayCategory(item.category)}
                            </p>
                          </div>

                          <button
                            type="button"
                            onClick={() => handleRemoveItem(index)}
                            className="flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-red-50 hover:text-red-500"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>

                        <textarea
                          value={item.memo ?? ""}
                          onChange={(event) =>
                            handleMemoChange(index, event.target.value)
                          }
                          rows={2}
                          placeholder="메모를 입력하세요"
                          className="mt-4 w-full resize-none rounded-xl border border-hairline bg-white px-3 py-2 text-sm outline-none focus:border-primary"
                        />

                        <div className="mt-3 flex gap-2">
                          <button
                            type="button"
                            onClick={() => handleMoveUp(index)}
                            disabled={index === 0}
                            className="flex items-center gap-1 rounded-lg border border-hairline bg-white px-3 py-1.5 text-xs disabled:opacity-40"
                          >
                            <ArrowUp className="h-3.5 w-3.5" />
                            위로
                          </button>

                          <button
                            type="button"
                            onClick={() => handleMoveDown(index)}
                            disabled={index === items.length - 1}
                            className="flex items-center gap-1 rounded-lg border border-hairline bg-white px-3 py-1.5 text-xs disabled:opacity-40"
                          >
                            <ArrowDown className="h-3.5 w-3.5" />
                            아래로
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* 하단 버튼 */}

            <div className="flex justify-end gap-3 pb-10">
              <button
                type="button"
                onClick={() => router.back()}
                disabled={saving}
                className="rounded-xl border border-hairline bg-white px-5 py-3 text-sm font-bold text-muted disabled:opacity-60"
              >
                취소
              </button>

              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="rounded-xl bg-primary px-6 py-3 text-sm font-bold text-white disabled:opacity-60"
              >
                {saving ? "수정 중..." : "수정 완료"}
              </button>
            </div>
          </div>
        </div>
      </AppShell>

      {/* =====================================================
       * 일반 알림창
       *
       * 외부 CommonAlert 파일 사용 안 함
       * ===================================================== */}

      {alertOpen && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/35 px-4">
          <div className="w-full max-w-lg overflow-hidden rounded-3xl bg-white shadow-2xl">
            <div className="px-8 py-8">
              <p className="text-lg leading-7 text-ink">{alertMessage}</p>
            </div>

            <div className="flex justify-end border-t border-hairline px-6 py-4">
              <button
                type="button"
                onClick={handleAlertClose}
                className="rounded-lg px-4 py-2 text-base font-bold text-primary transition-colors hover:bg-primary/5"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
