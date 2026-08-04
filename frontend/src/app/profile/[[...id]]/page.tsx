"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  Settings,
  Users,
  UserPlus,
  X,
  UserMinus,
  MoreHorizontal,
} from "lucide-react";

import AppShell from "@/components/AppShell";
import { apiFetchJson, getImageUrl } from "@/lib/api";
import { moodLabel } from "@/lib/mood";
import { buildListHref } from "@/lib/listNavigation";
import { getStoredUser } from "@/lib/user";

const tabs = ["리스트", "포스트", "저장함"] as const;
type ProfileTab = (typeof tabs)[number];

/* =========================================================
 * 사용자 프로필
 * ========================================================= */

interface UserProfile {
  id: number;
  nickname: string;
  profileImage: string | null;
  email: string;
  provider: "LOCAL" | "KAKAO";
  createdAt: string;
  isOwnProfile: boolean;
  isFollowing: boolean;
}

/* =========================================================
 * 팔로우 사용자
 * ========================================================= */

interface FollowUser {
  userId: number;
  nickname: string;
  profileImage: string | null;
  isFollowedByMe: boolean;
}

/* =========================================================
 * 프로필 리스트
 * ========================================================= */

interface ProfileList {
  id: number;
  userId: number;
  title: string;
  description: string;
  moodTag: string;
  itemCount: number;
}

/* =========================================================
 * 리스트 페이징 응답
 * ========================================================= */

interface ProfileListsResponse {
  lists: ProfileList[];
  totalPages: number;
  totalElements: number;
}

/* =========================================================
 * 피드 응답
 * ========================================================= */

interface FeedListPageResponse {
  feeds: {
    feedId: number;
    content: string;
    nickname: string;
    imageUrl?: string | null;
    likeCount: number;
    createdAt: string;
    restaurantId?: number | null;
    restaurantName?: string | null;
    moodTag?: string | null;
  }[];
}

/* =========================================================
 * 저장한 리스트
 * ========================================================= */

interface SavedList {
  listId: number;
  nickname: string;
  title: string;
  description: string;
  moodTag: string;
  items: unknown[];
  savedAt: string;
}

interface SavedListsResponse {
  content: SavedList[];
  totalPages: number;
}

/* =========================================================
 * 프로필 페이지
 * ========================================================= */

export default function ProfilePage() {
  const params = useParams();
  const router = useRouter();

  const rawId = params.id;

  const paramId = Array.isArray(rawId) ? rawId[0] : rawId;

  const targetUserId = paramId ? Number(paramId) : getStoredUser()?.userId;

  /* ---------------------------------------------------------
   * 프로필 상태
   * --------------------------------------------------------- */

  const [user, setUser] = useState<UserProfile | null>(null);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  /* ---------------------------------------------------------
   * 탭
   * --------------------------------------------------------- */

  const [activeTab, setActiveTab] = useState<ProfileTab>("리스트");

  const [tabLoading, setTabLoading] = useState(false);

  const [tabError, setTabError] = useState("");

  useEffect(() => {
    const syncTabFromUrl = () => {
      const tab = new URLSearchParams(window.location.search).get("tab");
      const nextTab: ProfileTab = tabs.includes(tab as ProfileTab)
        ? (tab as ProfileTab)
        : "리스트";

      setActiveTab((current) => (current === nextTab ? current : nextTab));
    };

    syncTabFromUrl();
    window.addEventListener("popstate", syncTabFromUrl);

    return () => {
      window.removeEventListener("popstate", syncTabFromUrl);
    };
  }, []);

  /* ---------------------------------------------------------
   * 팔로우
   * --------------------------------------------------------- */

  const [isFollowing, setIsFollowing] = useState(false);

  const [followLoading, setFollowLoading] = useState(false);

  const [showFollowers, setShowFollowers] = useState(false);

  const [showFollowings, setShowFollowings] = useState(false);

  const [followerCount, setFollowerCount] = useState<number | null>(null);

  const [followingCount, setFollowingCount] = useState<number | null>(null);

  const [openMenuFeedId, setOpenMenuFeedId] = useState<number | null>(null);

  /* ---------------------------------------------------------
   * 탭 데이터
   * --------------------------------------------------------- */

  const [myLists, setMyLists] = useState<ProfileList[]>([]);

  const [myPosts, setMyPosts] = useState<FeedListPageResponse["feeds"]>([]);

  const [savedLists, setSavedLists] = useState<SavedList[]>([]);

  /* ---------------------------------------------------------
   * 리스트 페이징
   * --------------------------------------------------------- */

  const [listPage, setListPage] = useState(0);

  const [listTotalPages, setListTotalPages] = useState(0);

  const [loadingMoreLists, setLoadingMoreLists] = useState(false);

  const [savedPage, setSavedPage] = useState(0);

  const [savedTotalPages, setSavedTotalPages] = useState(0);

  const [loadingMoreSavedLists, setLoadingMoreSavedLists] = useState(false);

  /* =========================================================
   * 프로필 + 팔로우 수 조회
   * ========================================================= */

  useEffect(() => {
    const load = async () => {
      if (!targetUserId) {
        setError("로그인이 필요합니다.");

        setLoading(false);

        return;
      }

      setLoading(true);

      setError("");

      try {
        const [profileRes, countRes] = await Promise.all([
          apiFetchJson<UserProfile>(`/api/v1/users/${targetUserId}`),

          apiFetchJson<{
            userId: number;
            followerCount: number;
            followingCount: number;
          }>(`/api/v1/follows/users/${targetUserId}/count`),
        ]);

        if (profileRes.ok && profileRes.data) {
          setUser(profileRes.data);

          setIsFollowing(profileRes.data.isFollowing);
        } else {
          setError(profileRes.message || "프로필을 불러오지 못했습니다.");
        }

        if (countRes.ok && countRes.data) {
          setFollowerCount(countRes.data.followerCount);

          setFollowingCount(countRes.data.followingCount);
        }
      } catch (error) {
        console.error("프로필 조회 실패:", error);

        setError("프로필을 불러오는 중 오류가 발생했습니다.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [targetUserId]);

  /* =========================================================
   * 탭 데이터 최초 조회
   * ========================================================= */

  useEffect(() => {
    if (!targetUserId || !user) {
      return;
    }

    let cancelled = false;

    const loadTab = async () => {
      setTabLoading(true);
      setTabError("");

      try {
        /* =====================================================
         * 리스트
         * ===================================================== */

        if (activeTab === "리스트") {
          /* 내 프로필 */

          if (user.isOwnProfile) {
            const res = await apiFetchJson<ProfileListsResponse>(
              "/api/v1/lists?page=0&size=10",
            );

            if (cancelled) {
              return;
            }

            if (res.ok && res.data) {
              setMyLists(res.data.lists);

              setListPage(0);

              setListTotalPages(res.data.totalPages);
            } else {
              setMyLists([]);

              setListPage(0);

              setListTotalPages(0);

              setTabError(res.message || "리스트를 불러오지 못했습니다.");
            }
          } else {
            /* 다른 사람 프로필 */
            const res = await apiFetchJson<ProfileListsResponse>(
              `/api/v1/lists/users/${targetUserId}?page=0&size=10`,
            );

            if (cancelled) {
              return;
            }

            if (res.ok && res.data) {
              setMyLists(res.data.lists);

              setListPage(0);

              setListTotalPages(res.data.totalPages);
            } else {
              setMyLists([]);

              setListPage(0);

              setListTotalPages(0);

              setTabError(res.message || "리스트를 불러오지 못했습니다.");
            }
          }
        } else if (activeTab === "포스트") {
          /* =====================================================
           * 포스트
           * ===================================================== */
          const res = await apiFetchJson<FeedListPageResponse>(
            `/api/v1/feeds?userId=${targetUserId}`,
          );

          if (cancelled) {
            return;
          }

          if (res.ok && res.data) {
            setMyPosts(res.data.feeds);
          } else {
            setMyPosts([]);
            setTabError(res.message || "포스트를 불러오지 못했습니다.");
          }
        } else if (activeTab === "저장함" && user.isOwnProfile) {
          /* =====================================================
           * 저장함
           * ===================================================== */
          const res = await apiFetchJson<SavedListsResponse>(
            "/api/v1/restaurant_lists/saved?page=0&size=10",
          );

          if (cancelled) {
            return;
          }

          if (res.ok && res.data) {
            setSavedLists(res.data.content ?? []);
            setSavedPage(0);
            setSavedTotalPages(res.data.totalPages);
          } else {
            setSavedLists([]);
            setSavedPage(0);
            setSavedTotalPages(0);
            setTabError(
              res.message || "저장한 리스트를 불러오지 못했습니다.",
            );
          }
        }
      } catch (error) {
        if (cancelled) {
          return;
        }

        console.error("프로필 탭 조회 실패:", error);

        setTabError("프로필 탭을 불러오는 중 오류가 발생했습니다.");

        if (activeTab === "리스트") {
          setMyLists([]);

          setListPage(0);

          setListTotalPages(0);
        }

        if (activeTab === "포스트") {
          setMyPosts([]);
        }

        if (activeTab === "저장함") {
          setSavedLists([]);
          setSavedPage(0);
          setSavedTotalPages(0);
        }
      } finally {
        if (!cancelled) {
          setTabLoading(false);
        }
      }
    };

    void loadTab();

    return () => {
      cancelled = true;
    };
  }, [activeTab, targetUserId, user]);

  /* =========================================================
   * 리스트 더보기
   * ========================================================= */

  const loadMoreLists = async () => {
    if (!targetUserId || !user) {
      return;
    }

    if (loadingMoreLists) {
      return;
    }

    const nextPage = listPage + 1;

    if (nextPage >= listTotalPages) {
      return;
    }

    setLoadingMoreLists(true);

    try {
      /* 내 프로필 */

      if (user.isOwnProfile) {
        const res = await apiFetchJson<ProfileListsResponse>(
          `/api/v1/lists?page=${nextPage}&size=10`,
        );

        if (!res.ok || !res.data) {
          alert(res.message || "다음 리스트를 불러오지 못했습니다.");

          return;
        }

        const data = res.data;

        setMyLists((prev) => {
          const existingIds = new Set(prev.map((list) => list.id));

          const newLists = data.lists.filter(
            (list) => !existingIds.has(list.id),
          );

          return [...prev, ...newLists];
        });

        setListPage(nextPage);

        setListTotalPages(data.totalPages);

        return;
      }

      /* 다른 사람 프로필 */

      const res = await apiFetchJson<ProfileListsResponse>(
        `/api/v1/lists/users/${targetUserId}?page=${nextPage}&size=10`,
      );

      if (!res.ok || !res.data) {
        alert(res.message || "다음 리스트를 불러오지 못했습니다.");

        return;
      }

      const data = res.data;

      setMyLists((prev) => {
        const existingIds = new Set(prev.map((list) => list.id));

        const newLists = data.lists.filter(
          (list) => !existingIds.has(list.id),
        );

        return [...prev, ...newLists];
      });

      setListPage(nextPage);

      setListTotalPages(data.totalPages);
    } catch (error) {
      console.error("프로필 리스트 추가 조회 실패:", error);

      alert("다음 리스트를 불러오는 중 오류가 발생했습니다.");
    } finally {
      setLoadingMoreLists(false);
    }
  };

  const loadMoreSavedLists = async () => {
    if (loadingMoreSavedLists) {
      return;
    }

    const nextPage = savedPage + 1;

    if (nextPage >= savedTotalPages) {
      return;
    }

    setLoadingMoreSavedLists(true);

    try {
      const res = await apiFetchJson<SavedListsResponse>(
        `/api/v1/restaurant_lists/saved?page=${nextPage}&size=10`,
      );

      if (!res.ok || !res.data) {
        alert(res.message || "다음 저장 리스트를 불러오지 못했습니다.");

        return;
      }

      const data = res.data;

      setSavedLists((prev) => {
        const existingIds = new Set(prev.map((list) => list.listId));
        const newLists = data.content.filter(
          (list) => !existingIds.has(list.listId),
        );

        return [...prev, ...newLists];
      });

      setSavedPage(nextPage);
      setSavedTotalPages(data.totalPages);
    } catch (requestError) {
      console.error("프로필 저장 리스트 추가 조회 실패:", requestError);

      alert("다음 저장 리스트를 불러오는 중 오류가 발생했습니다.");
    } finally {
      setLoadingMoreSavedLists(false);
    }
  };

  const handleEditFeed = (post: FeedListPageResponse["feeds"][number]) => {
    sessionStorage.setItem(
      "editingFeed",
      JSON.stringify({
        feedId: post.feedId,
        content: post.content,
        restaurantId: post.restaurantId,
        restaurantName: post.restaurantName,
        moodTag: post.moodTag ?? null,
        imageUrl: post.imageUrl,
        returnUrl: `${window.location.pathname}${window.location.search}`,
      }),
    );
    setOpenMenuFeedId(null);
    router.push(`/feed/write?edit=${post.feedId}`);
  };

  const handleTabChange = (tab: ProfileTab) => {
    setActiveTab(tab);

    const params = new URLSearchParams(window.location.search);
    params.set("tab", tab);

    router.replace(`${window.location.pathname}?${params.toString()}`, {
      scroll: false,
    });
  };

  const handleDeleteFeed = async (feedId: number) => {
    const confirmed = window.confirm("피드를 삭제하시겠습니까?");
    if (!confirmed) return;

    const res = await apiFetchJson(`/api/v1/feeds/${feedId}`, {
      method: "DELETE",
    });

    if (res.ok) {
      setMyPosts((prev) => prev.filter((post) => post.feedId !== feedId));

      setOpenMenuFeedId(null);

      window.dispatchEvent(new Event("feed-state-change"));
    } else {
      alert(res.message || "피드 삭제에 실패했습니다.");
    }
  };

  /* =========================================================
   * 팔로우 / 언팔로우
   * ========================================================= */

  const handleFollowToggle = async () => {
    if (!user || user.isOwnProfile) {
      return;
    }

    const nextFollowing = !isFollowing;

    if (!nextFollowing) {
      const confirmed = window.confirm("언팔로우 하시겠습니까?");

      if (!confirmed) {
        return;
      }
    }

    setFollowLoading(true);

    try {
      const res = nextFollowing
        ? await apiFetchJson(`/api/v1/follows/${user.id}`, {
            method: "POST",
          })
        : await apiFetchJson(`/api/v1/follows/${user.id}`, {
            method: "DELETE",
          });

      if (res.ok) {
        setIsFollowing(nextFollowing);

        setUser((prev) =>
          prev
            ? {
                ...prev,
                isFollowing: nextFollowing,
              }
            : prev,
        );

        setFollowerCount((prev) => {
          if (prev === null) {
            return prev;
          }

          return nextFollowing ? prev + 1 : Math.max(0, prev - 1);
        });

        window.dispatchEvent(new Event("follow-state-change"));
      } else {
        alert(res.message || "팔로우 처리에 실패했습니다.");
      }
    } catch (error) {
      console.error("팔로우 처리 실패:", error);

      alert("팔로우 처리 중 오류가 발생했습니다.");
    } finally {
      setFollowLoading(false);
    }
  };

  /* =========================================================
   * 로딩
   * ========================================================= */

  if (loading) {
    return (
      <AppShell>
        <div className="flex h-96 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </AppShell>
    );
  }

  /* =========================================================
   * 오류
   * ========================================================= */

  if (error || !user) {
    return (
      <AppShell>
        <p className="py-20 text-center text-sm text-red-500">
          {error || "프로필을 불러오지 못했습니다."}
        </p>
      </AppShell>
    );
  }

  /* =========================================================
   * 화면
   * ========================================================= */

  return (
    <AppShell>
      <div className="space-y-5">
        {/* =====================================================
         * 프로필 헤더
         * ===================================================== */}

        <div className="rounded-2xl border border-hairline-soft bg-surface p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-5">
              <img
                src={getImageUrl(user.profileImage) ?? "/default-profile.png"}
                alt="프로필"
                className="h-20 w-20 rounded-full object-cover ring-4 ring-primary/15"
              />

              <div>
                <h1 className="text-[22px] font-bold tracking-tight text-ink">
                  {user.nickname}
                </h1>

                <p className="text-sm text-muted">{user.email}</p>

                <p className="mt-1 text-xs text-muted-soft">
                  {user.provider === "KAKAO"
                    ? "카카오 로그인"
                    : "이메일 로그인"}
                </p>

                <div className="mt-3 flex gap-4 text-sm">
                  <button
                    type="button"
                    onClick={() => setShowFollowers(true)}
                    className="flex items-center gap-1.5 font-medium text-body transition-colors hover:text-primary"
                  >
                    <Users className="h-4 w-4 text-muted" />
                    팔로워{" "}
                    <span className="font-bold text-ink">
                      {followerCount ?? "-"}
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setShowFollowings(true)}
                    className="flex items-center gap-1.5 font-medium text-body transition-colors hover:text-primary"
                  >
                    <UserPlus className="h-4 w-4 text-muted" />
                    팔로잉{" "}
                    <span className="font-bold text-ink">
                      {followingCount ?? "-"}
                    </span>
                  </button>
                </div>
              </div>
            </div>

            {user.isOwnProfile && (
              <Link
                href="/profile/edit"
                className="rounded-full border border-hairline bg-surface-soft p-2 text-muted hover:text-ink"
              >
                <Settings className="h-5 w-5" />
              </Link>
            )}
          </div>

          <div className="mt-6 flex gap-3">
            {user.isOwnProfile ? (
              <Link
                href="/profile/edit"
                className="rounded-full border border-hairline bg-surface-soft px-4 py-2 text-sm font-bold text-ink transition-colors hover:bg-white"
              >
                프로필 수정
              </Link>
            ) : (
              <button
                type="button"
                onClick={handleFollowToggle}
                disabled={followLoading}
                className={`rounded-full px-5 py-2 text-sm font-bold transition-colors ${
                  isFollowing
                    ? "bg-surface-strong text-ink hover:bg-hairline"
                    : "bg-primary text-white hover:bg-primary-active"
                }`}
              >
                {followLoading
                  ? "처리 중..."
                  : isFollowing
                    ? "팔로잉"
                    : "팔로우"}
              </button>
            )}
          </div>
        </div>

        {/* =====================================================
         * 탭
         * ===================================================== */}

        <div className="flex border-b border-hairline-soft">
          {tabs.map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => handleTabChange(tab)}
              className={`relative px-4 py-3 text-sm font-bold transition-colors ${
                activeTab === tab ? "text-primary" : "text-muted hover:text-ink"
              }`}
            >
              {tab}

              {activeTab === tab && (
                <span className="absolute bottom-0 left-0 h-0.5 w-full bg-primary" />
              )}
            </button>
          ))}
        </div>

        {/* =====================================================
         * 탭 내용
         * ===================================================== */}

        <div>
          {tabLoading ? (
            <div className="flex h-40 items-center justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : (
            <>
              {tabError && (
                <p className="py-4 text-center text-sm text-red-500">
                  {tabError}
                </p>
              )}

              {/* =================================================
               * 리스트
               * ================================================= */}

              {activeTab === "리스트" && (
                <>
                  {myLists.length === 0 ? (
                    !tabError && (
                      <p className="py-10 text-center text-sm text-muted">
                        등록된 리스트가 없습니다.
                      </p>
                    )
                  ) : (
                    <>
                      <div className="grid gap-4 sm:grid-cols-2">
                        {myLists.map((list) => (
                          <Link
                            key={list.id}
                            href={buildListHref(
                              list.id,
                              user.isOwnProfile ? "my" : "other",
                            )}
                            className="overflow-hidden rounded-2xl border border-hairline-soft bg-surface shadow-sm transition-colors hover:border-primary/30"
                          >
                            <div className="h-36 bg-surface-strong">
                              <img
                                src="/list-placeholder.png"
                                alt={list.title}
                                className="h-full w-full object-cover"
                              />
                            </div>

                            <div className="p-4">
                              <div className="flex items-center justify-between gap-2">
                                <h3 className="truncate text-base font-bold text-ink">
                                  {list.title}
                                </h3>

                                <span className="shrink-0 rounded-full bg-tag-mood px-2 py-0.5 text-[10px] font-bold text-ink">
                                  {list.moodTag}
                                </span>
                              </div>

                              <div className="mt-2 text-xs text-muted-soft">
                                <span>식당 {list.itemCount}개</span>
                              </div>
                            </div>
                          </Link>
                        ))}
                      </div>

                      {/* =========================================
                       * 리스트 더보기
                       * ========================================= */}

                      {listPage + 1 < listTotalPages && (
                        <button
                          type="button"
                          onClick={() => void loadMoreLists()}
                          disabled={loadingMoreLists}
                          className="mt-4 w-full rounded-xl border border-hairline-soft bg-surface px-4 py-3 text-sm font-bold text-muted transition-colors hover:bg-surface-soft hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {loadingMoreLists ? "불러오는 중..." : "더보기"}
                        </button>
                      )}
                    </>
                  )}
                </>
              )}

              {/* =================================================
               * 포스트
               * ================================================= */}

              {activeTab === "포스트" && (
                <>
                  {myPosts.length === 0 ? (
                    !tabError && (
                      <p className="py-10 text-center text-sm text-muted">
                        작성한 포스트가 없습니다.
                      </p>
                    )
                  ) : (
                    <div className="space-y-4">
                      {myPosts.map((post) => (
                        <article
                          key={post.feedId}
                          onClick={() => router.push("/feed")}
                          className="rounded-2xl border border-hairline-soft bg-surface p-5 cursor-pointer hover:border-primary/30 transition-colors"
                        >
                          <div className="flex justify-end">
                            {user.isOwnProfile && (
                              <div className="relative">
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setOpenMenuFeedId((prev) =>
                                      prev === post.feedId ? null : post.feedId,
                                    );
                                  }}
                                  className="rounded-full p-1.5 text-muted hover:bg-surface-soft"
                                  aria-label="피드 메뉴"
                                >
                                  <MoreHorizontal className="h-4 w-4" />
                                </button>

                                {openMenuFeedId === post.feedId && (
                                  <div className="absolute right-0 top-8 z-20 w-28 overflow-hidden rounded-lg border border-hairline-soft bg-surface shadow-lg">
                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleEditFeed(post);
                                      }}
                                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-ink hover:bg-surface-soft"
                                    >
                                      수정
                                    </button>

                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleDeleteFeed(post.feedId);
                                      }}
                                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-500 hover:bg-surface-soft"
                                    >
                                      삭제
                                    </button>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>

                          {post.moodTag && (
                            <div className="mb-2">
                              <span className="inline-flex items-center rounded-full bg-tag-mood px-3 py-1 text-xs font-bold text-ink">
                                {moodLabel(post.moodTag)}
                              </span>
                            </div>
                          )}

                          <p className="text-sm leading-6 text-body">
                            {post.content}
                          </p>

                          {post.imageUrl && (
                            <div className="mt-3 flex justify-center">
                              <div className="w-full max-w-2xl aspect-[4/5] overflow-hidden rounded-xl border border-hairline-soft">
                                <img
                                  src={getImageUrl(post.imageUrl) ?? undefined}
                                  alt="피드 이미지"
                                  className="h-full w-full object-cover"
                                />
                              </div>
                            </div>
                          )}

                          <p className="mt-2 text-xs text-muted-soft">
                            좋아요 {post.likeCount} ·{" "}
                            {new Date(post.createdAt).toLocaleDateString()}
                          </p>
                        </article>
                      ))}
                    </div>
                  )}
                </>
              )}

              {/* =================================================
               * 저장함
               * ================================================= */}

              {activeTab === "저장함" && (
                <>
                  {!user.isOwnProfile ? (
                    <p className="py-10 text-center text-sm text-muted">
                      다른 사용자의 저장함은 볼 수 없습니다.
                    </p>
                  ) : savedLists.length === 0 ? (
                    !tabError && (
                      <p className="py-10 text-center text-sm text-muted">
                        저장한 리스트가 없습니다.
                      </p>
                    )
                  ) : (
                    <>
                      <div className="grid gap-4 sm:grid-cols-2">
                        {savedLists.map((list) => (
                          <Link
                            key={list.listId}
                            href={buildListHref(list.listId, "saved")}
                            className="overflow-hidden rounded-2xl border border-hairline-soft bg-surface shadow-sm transition-colors hover:border-primary/30"
                          >
                            <div className="h-36 bg-surface-strong">
                              <img
                                src="/list-placeholder.png"
                                alt={list.title}
                                className="h-full w-full object-cover"
                              />
                            </div>

                            <div className="p-4">
                              <div className="flex items-center justify-between gap-2">
                                <h3 className="truncate text-base font-bold text-ink">
                                  {list.title}
                                </h3>

                                <span className="shrink-0 rounded-full bg-tag-mood px-2 py-0.5 text-[10px] font-bold text-ink">
                                  {list.moodTag}
                                </span>
                              </div>

                              <p className="mt-1 text-xs text-muted">
                                by {list.nickname}
                              </p>

                              <div className="mt-2 text-xs text-muted-soft">
                                <span>식당 {list.items.length}개</span>
                              </div>
                            </div>
                          </Link>
                        ))}
                      </div>

                      {savedPage + 1 < savedTotalPages && (
                        <button
                          type="button"
                          onClick={() => void loadMoreSavedLists()}
                          disabled={loadingMoreSavedLists}
                          className="mt-4 w-full rounded-xl border border-hairline-soft bg-surface px-4 py-3 text-sm font-bold text-muted transition-colors hover:bg-surface-soft hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {loadingMoreSavedLists ? "불러오는 중..." : "더보기"}
                        </button>
                      )}
                    </>
                  )}
                </>
              )}
            </>
          )}
        </div>

        {/* =====================================================
         * 팔로워 모달
         * ===================================================== */}

        {showFollowers && targetUserId && (
          <FollowListModal
            title="팔로워 목록"
            type="followers"
            userId={targetUserId}
            onClose={() => setShowFollowers(false)}
            onFollowChange={(nextFollowing) => {
              if (!user.isOwnProfile) {
                return;
              }

              setFollowingCount((prev) => {
                if (prev === null) {
                  return prev;
                }

                return nextFollowing ? prev + 1 : Math.max(0, prev - 1);
              });
            }}
          />
        )}

        {/* =====================================================
         * 팔로잉 모달
         * ===================================================== */}

        {showFollowings && targetUserId && (
          <FollowListModal
            title="팔로잉 목록"
            type="followings"
            userId={targetUserId}
            onClose={() => setShowFollowings(false)}
            onFollowChange={(nextFollowing) => {
              if (!user.isOwnProfile) {
                return;
              }

              setFollowingCount((prev) => {
                if (prev === null) {
                  return prev;
                }

                return nextFollowing ? prev + 1 : Math.max(0, prev - 1);
              });
            }}
          />
        )}
      </div>
    </AppShell>
  );
}

/* =========================================================
 * 팔로우 목록 모달 Props
 * ========================================================= */

interface FollowListModalProps {
  title: string;
  type: "followers" | "followings";
  userId: number;
  onClose: () => void;
  onFollowChange?: (nextFollowing: boolean) => void;
}

/* =========================================================
 * 팔로우 목록 모달
 * ========================================================= */

function FollowListModal({
  title,
  type,
  userId,
  onClose,
  onFollowChange,
}: FollowListModalProps) {
  const [users, setUsers] = useState<FollowUser[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const endpoint =
    type === "followers"
      ? `/api/v1/follows/users/${userId}/followers`
      : `/api/v1/follows/users/${userId}/followings`;

  /* =========================================================
   * 팔로우 목록 조회
   * ========================================================= */

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);

      setError("");

      try {
        const res = await apiFetchJson<{
          content: FollowUser[];
        }>(endpoint);

        if (cancelled) {
          return;
        }

        if (res.ok && res.data) {
          setUsers(res.data.content ?? []);
        } else {
          setUsers([]);

          setError(res.message || "목록을 불러오지 못했습니다.");
        }
      } catch (error) {
        if (cancelled) {
          return;
        }

        console.error("팔로우 목록 조회 실패:", error);

        setUsers([]);

        setError("목록을 불러오는 중 오류가 발생했습니다.");
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [endpoint]);

  /* =========================================================
   * 모달 안 팔로우 / 언팔로우
   * ========================================================= */

  const handleToggle = async (
    targetUserId: number,
    currentlyFollowing: boolean,
  ) => {
    if (currentlyFollowing) {
      const confirmed = window.confirm("언팔로우 하시겠습니까?");

      if (!confirmed) {
        return;
      }
    }

    try {
      const res = await apiFetchJson(`/api/v1/follows/${targetUserId}`, {
        method: currentlyFollowing ? "DELETE" : "POST",
      });

      if (res.ok) {
        const nextFollowing = !currentlyFollowing;

        setUsers((prev) =>
          prev.map((followUser) =>
            followUser.userId === targetUserId
              ? {
                  ...followUser,
                  isFollowedByMe: nextFollowing,
                }
              : followUser,
          ),
        );

        onFollowChange?.(nextFollowing);

        window.dispatchEvent(new Event("follow-state-change"));
      } else {
        alert(res.message || "팔로우 처리에 실패했습니다.");
      }
    } catch (error) {
      console.error("팔로우 처리 실패:", error);

      alert("팔로우 처리 중 오류가 발생했습니다.");
    }
  };

  /* =========================================================
   * 모달
   * ========================================================= */

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 animate-in fade-in-50">
      <div className="w-full max-w-sm rounded-2xl border border-hairline-soft bg-surface p-5 shadow-lg">
        <div className="flex items-center justify-between border-b border-hairline-soft pb-3">
          <h2 className="text-base font-bold text-ink">{title}</h2>

          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1 text-muted hover:bg-surface-soft"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <ul className="mt-4 max-h-60 space-y-3 overflow-y-auto">
          {loading ? (
            <li className="py-6 text-center text-sm text-muted">
              불러오는 중...
            </li>
          ) : error ? (
            <li className="py-6 text-center text-sm text-red-500">{error}</li>
          ) : users.length === 0 ? (
            <li className="py-6 text-center text-sm text-muted">
              목록이 비어있습니다.
            </li>
          ) : (
            users.map((followUser) => (
              <li
                key={followUser.userId}
                className="flex items-center justify-between"
              >
                <Link
                  href={`/profile/${followUser.userId}`}
                  onClick={onClose}
                  className="flex items-center gap-3"
                >
                  <img
                    src={
                      getImageUrl(followUser.profileImage) ??
                      "/default-profile.png"
                    }
                    alt={followUser.nickname}
                    className="h-9 w-9 rounded-full object-cover"
                  />

                  <span className="text-sm font-bold text-ink hover:text-primary">
                    {followUser.nickname}
                  </span>
                </Link>

                <button
                  type="button"
                  onClick={() =>
                    void handleToggle(
                      followUser.userId,
                      followUser.isFollowedByMe,
                    )
                  }
                  className="flex items-center gap-1 rounded-full bg-surface-soft px-3 py-1 text-xs font-bold text-muted hover:text-primary"
                >
                  <UserMinus className="h-3 w-3" />

                  {followUser.isFollowedByMe ? "언팔로우" : "팔로우"}
                </button>
              </li>
            ))
          )}
        </ul>
      </div>
    </div>
  );
}
