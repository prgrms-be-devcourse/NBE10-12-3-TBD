"use client";

import { useState, useEffect, useRef, Suspense, startTransition } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, X, ImagePlus, Send, Lightbulb, Search } from "lucide-react";
import AppShell from "@/components/AppShell";
import { apiFetch, apiFetchJson, getImageUrl } from "@/lib/api";
import { resizeImageToInstagram } from "@/lib/image";
import { KAKAO_JS_KEY } from "@/lib/kakao";
import { MOOD_TAGS, type MoodTagValue } from "@/lib/mood";

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
  restaurantId?: number;
}

interface KakaoPlaceItem {
  id: string;
  place_name: string;
  category_name: string;
  address_name: string;
  road_address_name: string;
  phone: string;
  x: string;
  y: string;
}

interface EditingFeed {
  feedId: number;
  content: string;
  restaurantId: number | null;
  restaurantName: string | null;
  imageUrl?: string | null;
  moodTag?: string | null;
  returnUrl?: string;
}

const guideItems = [
  "방문한 식당을 태그하면 지도에서도 확인할 수 있어요.",
  "분위기 태그를 선택하면 비슷한 취향의 푸디들에게 노출돼요.",
  "솔직한 후기일수록 다른 사용자들에게 도움이 돼요.",
];

function WritePostContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const editFeedId = searchParams.get("edit");
  const isEditMode = Boolean(editFeedId);

  const kakaoKey = KAKAO_JS_KEY;

  const [content, setContent] = useState("");
  const [selectedMood, setSelectedMood] = useState<MoodTagValue | null>(null);
  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState<KakaoRestaurant[]>([]);
  const [selectedRestaurant, setSelectedRestaurant] =
    useState<KakaoRestaurant | null>(null);
  const [searching, setSearching] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [kakaoReady, setKakaoReady] = useState(false);
  const [kakaoLoadFailed, setKakaoLoadFailed] = useState(!kakaoKey);

  const [recentPosts, setRecentPosts] = useState<
    { feedId: number; nickname: string; content: string }[]
  >([]);
  const [feedImageFile, setFeedImageFile] = useState<File | null>(null);
  const [feedImagePreview, setFeedImagePreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // 언마운트 시 정리 effect에서 최신 미리보기 값을 읽기 위한 ref (effect는 마운트 시
  // 한 번만 등록되므로 state를 직접 의존성에 넣지 않고 ref로 최신값을 따라간다).
  const feedImagePreviewRef = useRef<string | null>(null);
  useEffect(() => {
    feedImagePreviewRef.current = feedImagePreview;
  }, [feedImagePreview]);

  useEffect(() => {
    if (!isEditMode || !editFeedId) return;

    const raw = sessionStorage.getItem("editingFeed");

    if (!raw) {
      alert("수정할 피드 정보를 찾을 수 없습니다.");
      router.replace("/feed");
      return;
    }

    try {
      const editingFeed = JSON.parse(raw) as EditingFeed;

      if (String(editingFeed.feedId) !== editFeedId) {
        alert("수정할 피드 정보가 올바르지 않습니다.");
        router.replace("/feed");
        return;
      }

      startTransition(() => {
        setContent(editingFeed.content);
        setFeedImagePreview(getImageUrl(editingFeed.imageUrl));
        setSelectedMood((editingFeed.moodTag as MoodTagValue | null) ?? null);

        if (editingFeed.restaurantId && editingFeed.restaurantName) {
          setSelectedRestaurant({
            restaurantId: editingFeed.restaurantId,
            kakaoPlaceId: "",
            name: editingFeed.restaurantName,
            category: "",
            address: "",
            roadAddress: "",
            region1: "",
            region2: "",
            region3: "",
            phone: "",
            lat: 0,
            lng: 0,
          });
        } else {
          setSelectedRestaurant(null);
        }
      });
    } catch {
      alert("수정할 피드 정보를 불러오지 못했습니다.");
      router.replace("/feed");
    }
  }, [isEditMode, editFeedId, router]);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const markReady = () => {
      if (window.kakao?.maps?.services) {
        setKakaoReady(true);
        setKakaoLoadFailed(false);
      }
    };

    const loadKakaoMaps = () => {
      if (!window.kakao?.maps) return;

      window.kakao.maps.load(() => {
        if (window.kakao?.maps?.services) {
          setKakaoReady(true);
          setKakaoLoadFailed(false);
        } else {
          setKakaoLoadFailed(true);
        }
      });
    };

    if (window.kakao?.maps?.services) {
      markReady();
      return;
    }

    if (window.kakao?.maps) {
      loadKakaoMaps();
      return;
    }

    const existingScript = document.getElementById(
      "kakao-map-sdk",
    ) as HTMLScriptElement | null;

    if (existingScript) {
      existingScript.addEventListener("load", loadKakaoMaps);
      existingScript.addEventListener("error", () => setKakaoLoadFailed(true));

      const check = window.setInterval(() => {
        if (window.kakao?.maps) {
          window.clearInterval(check);
          loadKakaoMaps();
        }
      }, 100);

      return () => {
        existingScript.removeEventListener("load", loadKakaoMaps);
        window.clearInterval(check);
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
    script.onload = loadKakaoMaps;
    script.onerror = () => setKakaoLoadFailed(true);

    document.head.appendChild(script);
  }, [kakaoKey]);

  useEffect(() => {
    const loadRecent = async () => {
      const res = await apiFetchJson<
        { feedId: number; nickname: string; content: string }[]
      >("/api/v1/feeds/recommend");
      if (res.ok && res.data) {
        setRecentPosts(res.data.slice(0, 3));
      }
    };

    loadRecent();
  }, []);

  const handleSearch = async (
    e?: React.FormEvent | React.MouseEvent | React.KeyboardEvent,
  ) => {
    e?.preventDefault();
    if (!query.trim()) return;

    if (kakaoLoadFailed) {
      alert("카카오맵 SDK를 불러오지 못했습니다.");
      return;
    }

    const services = window.kakao?.maps?.services;

    if (!kakaoReady || !services) {
      alert("카카오맵 SDK를 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
      return;
    }

    setSearching(true);

    const places = new services.Places();
    places.keywordSearch(
      query.trim(),
      (data: KakaoPlaceItem[], status: string) => {
        if (status === services.Status.OK) {
          const mapped: KakaoRestaurant[] = data.map((item) => {
            const addressParts = item.address_name
              ? item.address_name.split(" ")
              : [];
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
          setSearchResults(mapped);
        } else {
          alert("검색 결과를 불러오지 못했습니다.");
          setSearchResults([]);
        }
        setSearching(false);
      },
      {
        category_group_code: "FD6",
        size: 15,
      },
    );
  };

  // 서버에서 내려온 이미지 URL(수정 모드 초기값)은 revokeObjectURL 대상이 아니므로
  // blob: 미리보기만 골라서 해제한다.
  const revokeBlobPreview = (preview: string | null) => {
    if (preview?.startsWith("blob:")) {
      URL.revokeObjectURL(preview);
    }
  };

  const handleImageSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
      alert("10MB 이하의 이미지 파일만 첨부할 수 있습니다.");
      e.target.value = "";
      return;
    }

    try {
      const resized = await resizeImageToInstagram(file);
      // 이전에 만든 blob 미리보기 URL을 해제하지 않으면, 사진을 여러 번 바꿔 고를 때마다
      // 브라우저 메모리에 계속 쌓인다.
      revokeBlobPreview(feedImagePreview);
      setFeedImageFile(resized);
      setFeedImagePreview(URL.createObjectURL(resized));
    } catch {
      alert("이미지 처리에 실패했습니다.");
    }
    e.target.value = "";
  };

  const handleRemoveImage = () => {
    revokeBlobPreview(feedImagePreview);
    setFeedImageFile(null);
    setFeedImagePreview(null);
  };

  // 미리보기를 바꾸지 않고(예: 사진을 고른 채로) 다른 페이지로 이동해 컴포넌트가
  // 언마운트되는 경우에도 마지막 blob URL은 해제해야 한다.
  useEffect(() => {
    return () => {
      revokeBlobPreview(feedImagePreviewRef.current);
    };
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;

    setSubmitting(true);

    let restaurantId: number | null = null;

    if (selectedRestaurant) {
      if (selectedRestaurant.restaurantId) {
        restaurantId = selectedRestaurant.restaurantId;
      } else {
        const saveRes = await apiFetchJson<{ id: number }>(
          "/api/v1/restaurants",
          {
            method: "POST",
            body: JSON.stringify({
              kakaoPlaceId: selectedRestaurant.kakaoPlaceId,
              name: selectedRestaurant.name,
              categoryName: selectedRestaurant.category,
              address: selectedRestaurant.address,
              roadAddress: selectedRestaurant.roadAddress,
              region1: selectedRestaurant.region1,
              region2: selectedRestaurant.region2,
              region3: selectedRestaurant.region3,
              phone: selectedRestaurant.phone,
              lat: selectedRestaurant.lat,
              lng: selectedRestaurant.lng,
            }),
          },
        );

        if (!saveRes.ok || !saveRes.data) {
          alert(saveRes.message || "식당 저장에 실패했습니다.");
          setSubmitting(false);
          return;
        }

        restaurantId = saveRes.data.id;
      }
    }

    const formData = new FormData();

    if (isEditMode) {
      formData.append("content", content.trim());
      // moodTag를 보내지 않으면 서버가 기존 값을 해제한다(FeedUpdateRequest 참고).
      // 사용자가 태그를 해제한 경우 그대로 아무것도 보내지 않으면 된다.
      if (selectedMood) {
        formData.append("moodTag", selectedMood);
      }
      if (restaurantId !== null) {
        formData.append("restaurantId", String(restaurantId));
      }
      if (!feedImagePreview) {
        formData.append("deleteImage", "true");
      }
      if (feedImageFile) {
        formData.append("image", feedImageFile);
      }
    } else {
      formData.append(
        "feed",
        new Blob(
          [JSON.stringify({ content: content.trim(), restaurantId, moodTag: selectedMood })],
          {
            type: "application/json",
          },
        ),
      );
      if (feedImageFile) {
        formData.append("image", feedImageFile);
      }
    }

    const feedRes = await apiFetch(
      isEditMode ? `/api/v1/feeds/${editFeedId}` : "/api/v1/feeds",
      {
        method: isEditMode ? "PUT" : "POST",
        body: formData,
      },
    );
    const feedJson = await feedRes.json().catch(() => ({}));

    if (feedRes.ok) {
      let returnUrl = "/feed";

      if (isEditMode) {
        const raw = sessionStorage.getItem("editingFeed");

        if (raw) {
          const editingFeed = JSON.parse(raw) as EditingFeed;
          returnUrl = editingFeed.returnUrl ?? "/feed";
        }

        sessionStorage.removeItem("editingFeed");
      }

      router.push(returnUrl);
    } else {
      alert(
        feedJson.message ||
          (isEditMode
            ? "피드 수정에 실패했습니다."
            : "피드 작성에 실패했습니다."),
      );
    }

    setSubmitting(false);
  };

  return (
    <AppShell
      leftSidebar={
        <div className="sticky top-20 space-y-5">
          <LeftRecentPosts posts={recentPosts} />
        </div>
      }
      rightSidebar={
        <div className="space-y-5">
          <div className="rounded-2xl bg-surface p-4 border border-hairline-soft">
            <p className="text-sm font-bold text-ink mb-3">작성 가이드</p>
            <ul className="space-y-2.5">
              {guideItems.map((item, i) => (
                <li
                  key={i}
                  className="flex gap-2 text-xs text-body leading-relaxed"
                >
                  <Lightbulb className="h-3.5 w-3.5 shrink-0 text-primary mt-0.5" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>
      }
    >
      <div className="space-y-5">
        <div className="flex items-center justify-between">
          <Link
            href="/feed"
            className="flex items-center gap-1.5 text-sm font-semibold text-muted hover:text-ink transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            피드로 돌아가기
          </Link>
          <h2 className="text-xl font-bold text-ink">
            {isEditMode ? "피드 수정" : "새 포스트 작성"}
          </h2>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-2xl bg-surface p-6 border border-hairline-soft shadow-sm space-y-5"
        >
          {/* Tagged restaurant */}
          <div className="space-y-3">
            <label className="text-xs font-bold text-muted mb-2 block">
              태그된 식당 (선택)
            </label>
            {selectedRestaurant ? (
              <div className="flex items-center justify-between rounded-xl bg-primary-soft p-3">
                <div>
                  <p className="text-sm font-bold text-ink">
                    {selectedRestaurant.name}
                  </p>
                  <p className="text-xs text-muted">
                    {selectedRestaurant.roadAddress ||
                      selectedRestaurant.address}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedRestaurant(null)}
                  className="rounded-full p-1.5 text-muted hover:bg-white/50"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="식당명을 입력하세요"
                    className="flex-1 rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm focus:border-primary focus:outline-hidden"
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        handleSearch(e);
                      }
                    }}
                  />
                  <button
                    type="button"
                    onClick={handleSearch}
                    disabled={searching}
                    className="flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2.5 text-sm font-bold text-white hover:bg-primary-active transition-colors disabled:opacity-70"
                  >
                    <Search className="h-4 w-4" />
                    {searching ? "검색 중..." : "검색"}
                  </button>
                </div>
                <div className="space-y-2">
                  {searchResults.map((r) => (
                    <button
                      key={r.kakaoPlaceId}
                      type="button"
                      onClick={() => setSelectedRestaurant(r)}
                      className="w-full rounded-xl border border-hairline-soft bg-surface-soft p-3 text-left hover:border-primary/30 transition-colors"
                    >
                      <p className="text-sm font-bold text-ink">{r.name}</p>
                      <p className="text-xs text-muted">
                        {r.roadAddress || r.address}
                      </p>
                    </button>
                  ))}
                </div>
                <p className="text-xs text-muted">
                  식당을 선택하지 않아도 포스트를 작성할 수 있어요.
                </p>
              </>
            )}
          </div>

          {/* Mood tags */}
          <div>
            <label className="text-xs font-bold text-muted mb-2 block">
              분위기 태그
            </label>
            <div className="flex flex-wrap gap-2">
              {MOOD_TAGS.map((tag) => (
                <button
                  key={tag.value}
                  type="button"
                  onClick={() =>
                    setSelectedMood(selectedMood === tag.value ? null : tag.value)
                  }
                  className={`rounded-full px-4 py-1.5 text-xs font-bold transition-colors ${
                    selectedMood === tag.value
                      ? "bg-primary text-white"
                      : "bg-surface-soft text-muted hover:bg-hairline-soft"
                  }`}
                >
                  {tag.label}
                </button>
              ))}
            </div>
          </div>

          {/* Content */}
          <div>
            <label className="text-xs font-bold text-muted mb-2 block">
              포스트 내용
            </label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="식당에 대한 솔직한 후기를 남겨주세요."
              rows={6}
              maxLength={1000}
              className="w-full rounded-xl border border-hairline bg-surface-soft p-4 text-sm focus:border-primary focus:outline-hidden resize-none"
              required
            />
            <p className="mt-1 text-right text-xs text-muted-soft">
              {content.length}/1000
            </p>
          </div>

          {/* Photo upload */}
          <div>
            <label className="text-xs font-bold text-muted mb-2 block">
              사진 추가 (선택)
            </label>
            {feedImagePreview ? (
              <div className="relative w-full max-w-2xl aspect-[4/5] overflow-hidden rounded-xl border border-hairline-soft mx-auto">
                <img
                  src={feedImagePreview}
                  alt="피드 사진 미리보기"
                  className="h-full w-full object-cover"
                />
                <button
                  type="button"
                  onClick={handleRemoveImage}
                  className="absolute top-2 right-2 rounded-full bg-black/60 p-1.5 text-white hover:bg-black/80 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex w-full items-center justify-center gap-2 rounded-xl border-2 border-dashed border-hairline bg-surface-soft py-8 text-muted hover:border-primary/30 hover:text-primary transition-colors"
              >
                <ImagePlus className="h-5 w-5" />
                <span className="text-sm font-medium">클릭하여 사진 추가</span>
              </button>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept=".jpg,.jpeg,.png"
              className="hidden"
              onChange={handleImageSelect}
            />
          </div>

          {/* Submit */}
          <div className="flex justify-end pt-2">
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 rounded-xl bg-primary px-6 py-2.5 text-sm font-bold text-white hover:bg-primary-active transition-colors disabled:opacity-70"
            >
              <Send className="h-4 w-4" />
              {isEditMode
                ? submitting
                  ? "수정 중..."
                  : "수정하기"
                : submitting
                  ? "등록 중..."
                  : "포스트 올리기"}
            </button>
          </div>
        </form>
      </div>
    </AppShell>
  );
}

function LeftRecentPosts({
  posts,
}: {
  posts: { feedId: number; nickname: string; content: string }[];
}) {
  return (
    <div className="rounded-2xl bg-surface p-4 border border-hairline-soft">
      <p className="text-sm font-bold text-ink mb-3">최근 피드</p>
      {posts.length === 0 ? (
        <p className="text-xs text-muted">최근 피드가 없습니다.</p>
      ) : (
        <div className="space-y-3">
          {posts.map((p) => (
            <Link key={p.feedId} href={`/feed`} className="flex gap-2.5 group">
              <div className="h-7 w-7 shrink-0 rounded-full bg-primary/10 flex items-center justify-center text-xs font-bold text-primary">
                {p.nickname[0]}
              </div>
              <div>
                <p className="text-xs font-bold text-ink group-hover:text-primary transition-colors">
                  {p.nickname}
                </p>
                <p className="text-xs text-muted line-clamp-2">{p.content}</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
export default function WritePostPage() {
  return (
    <Suspense
      fallback={
        <AppShell>
          <div className="space-y-5">
            <div className="h-10 w-40 rounded-lg bg-surface-soft animate-pulse" />
            <div className="h-96 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
          </div>
        </AppShell>
      }
    >
      <WritePostContent />
    </Suspense>
  );
}
