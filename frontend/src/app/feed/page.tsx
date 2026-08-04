"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Heart,
  MessageCircle,
  MoreHorizontal,
  Plus,
  Pencil,
  RefreshCw,
  Trash2,
} from "lucide-react";
import AppShell, { SidebarProfile, SidebarCard } from "@/components/AppShell";
import { apiFetchJson, getImageUrl } from "@/lib/api";
import { getStoredUser, setStoredUser } from "@/lib/user";
import CommentModal from "@/components/CommentModal";

interface Feed {
  feedId: number;
  content: string;
  userId: number;
  nickname: string;
  profileImage: string | null;
  imageUrl?: string | null;
  likeCount: number;
  isLikedByMe: boolean;
  commentCount: number;
  restaurantId: number | null;
  restaurantName: string | null;
  createdAt: string;
}

interface FeedListPageResponse {
  feeds: Feed[];
  totalPages: number;
  totalElements: number;
  page: number;
  size: number;
}

interface RecommendFoodie {
  userId: number;
  nickname: string;
  profileImage: string | null;
}

const RECOMMEND_PAGE_SIZE = 20;

function pickUniqueFoodies(feeds: Feed[], currentUserId: number): RecommendFoodie[] {
  const seen = new Set<number>();
  const unique: RecommendFoodie[] = [];

  for (const feed of feeds) {
    if (feed.userId === currentUserId) continue;

    if (!seen.has(feed.userId)) {
      seen.add(feed.userId);
      unique.push({
        userId: feed.userId,
        nickname: feed.nickname,
        profileImage: feed.profileImage,
      });
    }

    if (unique.length >= 3) break;
  }

  return unique;
}

function FeedContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const activeTab =
    searchParams.get("tab") === "recommended" ? "recommended" : "following";
  const highlightFeedId = searchParams.get("highlight");

  const [posts, setPosts] = useState<Feed[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [recommendCursor, setRecommendCursor] = useState<number | null>(null);
  const [recommendPool, setRecommendPool] = useState<Feed[]>([]);
  const [recommendFoodies, setRecommendFoodies] = useState<RecommendFoodie[]>(
    [],
  );
  const [currentUserId, setCurrentUserId] = useState<number | null>(() => {
    const stored = getStoredUser();
    return stored?.userId ?? null;
  });
  const [commentModalOpen, setCommentModalOpen] = useState(false);
  const [activeCommentFeedId, setActiveCommentFeedId] = useState<number | null>(
    null,
  );
  const [openMenuFeedId, setOpenMenuFeedId] = useState<number | null>(null);

  // 추천 탭에 보여줄 목록은 별도 state로 동기화하지 않고 매 렌더링마다 recommendPool에서
  // 파생시킨다. state로 동기화하면(예전 방식) effect 안에서 posts/hasMore를 다시 계산해
  // setState해야 하는데, 이러면 매 스크롤마다 렌더링이 한 번 더 발생하고, 같은 동기 실행
  // 안에서 loadingMore를 true→false로 되돌리면 배칭 때문에 true인 상태가 화면에 반영조차
  // 안 된다. recommendPool 자체가 이미 최신 진실이므로 그걸 그대로 잘라 쓰면 충분하다.
  const recommendVisibleCount = (page + 1) * RECOMMEND_PAGE_SIZE;
  const displayedPosts =
    activeTab === "recommended"
      ? recommendPool.slice(0, recommendVisibleCount)
      : posts;
  const canLoadMore =
    activeTab === "recommended"
      ? recommendVisibleCount < recommendPool.length
      : hasMore;
  // 추천 탭에서는 추천 푸디도 이미 받아온 recommendPool에서 그대로 파생시킨다. handleFollow가
  // 팔로우 성공 시 recommendPool에서 그 작성자의 글을 지우므로, 이 값도 즉시 함께 갱신된다.
  const displayedFoodies =
    activeTab === "recommended" && currentUserId
      ? pickUniqueFoodies(recommendPool, currentUserId)
      : recommendFoodies;

  // highlight 파라미터가 있으면 해당 피드로 스크롤 + 하이라이트 (알림 클릭 진입)
  useEffect(() => {
    if (!highlightFeedId || loading) return;
    const el = document.getElementById(`feed-${highlightFeedId}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    el.classList.add("ring-2", "ring-primary");
    const timer = setTimeout(() => el.classList.remove("ring-2", "ring-primary"), 2000);
    return () => clearTimeout(timer);
  }, [highlightFeedId, loading, displayedPosts]);

  const handleOpenComments = (feedId: number) => {
    setActiveCommentFeedId(feedId);
    setCommentModalOpen(true);
  };

  const handleCloseComments = () => {
    setCommentModalOpen(false);
    setActiveCommentFeedId(null);
  };

  // 추천 탭의 displayedPosts는 recommendPool에서 파생되므로, 좋아요/댓글/삭제 같은 변경은
  // posts뿐 아니라 recommendPool에도 반영해야 화면에 그대로 유지된다.
  const updatePostEverywhere = (feedId: number, updater: (post: Feed) => Feed) => {
    const apply = (prev: Feed[]) =>
      prev.map((post) => (post.feedId === feedId ? updater(post) : post));
    setPosts(apply);
    setRecommendPool(apply);
  };

  const removePostEverywhere = (feedId: number) => {
    const apply = (prev: Feed[]) => prev.filter((post) => post.feedId !== feedId);
    setPosts(apply);
    setRecommendPool(apply);
  };

  // 팔로우한 사용자의 글은 백엔드에서도 추천 후보에서 제외되므로, 팔로우 성공 시 프론트도
  // posts/recommendPool에서 그 작성자의 글을 바로 지운다. 그대로 두면 좋아요/댓글 등
  // 다른 변경으로 recommendPool이 갱신될 때 방금 팔로우한 사람이 추천 푸디에 재등장한다.
  const removePostsByAuthorEverywhere = (userId: number) => {
    const apply = (prev: Feed[]) => prev.filter((post) => post.userId !== userId);
    setPosts(apply);
    setRecommendPool(apply);
  };

  const handleCommentCountChange = (feedId: number, count: number) => {
    updatePostEverywhere(feedId, (post) => ({ ...post, commentCount: count }));
  };

  useEffect(() => {
    apiFetchJson<{
      id: number;
      nickname: string;
      profileImage: string | null;
      email: string;
    }>("/api/v1/users/me").then((res) => {
      if (res.ok && res.data) {
        setStoredUser({
          userId: res.data.id,
          nickname: res.data.nickname,
          profileImage: res.data.profileImage,
          email: res.data.email,
        });

        setCurrentUserId(res.data.id);

        window.dispatchEvent(new Event("login-state-change"));
      }
    });
  }, []);

  // 팔로잉 탭: 서버가 id 역순으로 페이지네이션한 결과를 그대로 이어붙인다.
  useEffect(() => {
    if (activeTab !== "following") return;

    const load = async () => {
      if (page === 0) {
        setLoading(true);
      } else {
        setLoadingMore(true);
      }
      setError("");

      const res = await apiFetchJson<FeedListPageResponse>(
        `/api/v1/feeds/following?page=${page}&size=${RECOMMEND_PAGE_SIZE}`,
      );

      if (res.ok && res.data) {
        const { feeds, totalPages, size } = res.data;
        setPosts((prev) => (page === 0 ? feeds : [...prev, ...feeds]));
        setHasMore(feeds.length === size && page < totalPages - 1);
      } else {
        setError(res.message || "피드를 불러오지 못했습니다.");
        if (page === 0) setPosts([]);
      }

      if (page === 0) {
        setLoading(false);
      } else {
        setLoadingMore(false);
      }
    };

    load();
  }, [activeTab, page]);

  // 추천 탭: 점수순으로 이미 확정된 전체 후보 묶음(최대 300개)을 한 번에 받아와서
  // 스크롤에 따라 로컬에서 잘라 보여준다. 페이지별로 서버를 다시 호출하면 그 사이
  // 점수(좋아요/댓글 등)가 바뀌어 일부 피드가 노출 없이 누락될 수 있기 때문이다.
  useEffect(() => {
    if (activeTab !== "recommended") return;

    const loadPool = async () => {
      setLoading(true);
      setError("");

      const cursorParam =
        recommendCursor !== null ? `?beforeFeedId=${recommendCursor}` : "";
      const res = await apiFetchJson<Feed[]>(
        `/api/v1/feeds/recommend${cursorParam}`,
      );

      if (res.ok && res.data) {
        setRecommendPool(res.data);
      } else {
        setError(res.message || "피드를 불러오지 못했습니다.");
        setRecommendPool([]);
      }

      setLoading(false);
    };

    loadPool();
  }, [activeTab, recommendCursor]);

  const handleRefreshRecommend = () => {
    if (recommendPool.length === 0) return;
    const oldestFeedId = Math.min(...recommendPool.map((post) => post.feedId));
    setRecommendCursor(oldestFeedId);
    setRecommendPool([]);
    setPage(0);
  };

  // 추천 탭은 displayedFoodies가 recommendPool에서 직접 파생하므로, 여기서는 팔로잉 탭에
  // 표시할 추천 푸디만 네트워크로 받아온다.
  useEffect(() => {
    if (!currentUserId || activeTab === "recommended") return;

    const loadFoodies = async () => {
      const res = await apiFetchJson<Feed[]>("/api/v1/feeds/recommend");
      if (!res.ok || !res.data) return;
      setRecommendFoodies(pickUniqueFoodies(res.data, currentUserId));
    };

    loadFoodies();
  }, [currentUserId, activeTab]);

  const handleFollow = async (userId: number) => {
    const res = await apiFetchJson(`/api/v1/follows/${userId}`, {
      method: "POST",
    });

    if (res.ok) {
      removePostsByAuthorEverywhere(userId);
      setRecommendFoodies((prev) => prev.filter((f) => f.userId !== userId));
      window.dispatchEvent(new Event("follow-state-change"));
    } else {
      alert(res.message || "팔로우에 실패했습니다.");
    }
  };

  const handleLikeToggle = async (feedId: number, currentlyLiked: boolean) => {
    const res = await apiFetchJson(`/api/v1/feeds/${feedId}/like`, {
      method: currentlyLiked ? "DELETE" : "POST",
    });
    if (res.ok) {
      updatePostEverywhere(feedId, (post) => ({
        ...post,
        isLikedByMe: !currentlyLiked,
        likeCount: currentlyLiked ? post.likeCount - 1 : post.likeCount + 1,
      }));
    } else {
      alert(res.message || "좋아요 처리에 실패했습니다.");
    }
  };

  const handleEditFeed = (post: Feed) => {
    sessionStorage.setItem(
      "editingFeed",
      JSON.stringify({
        feedId: post.feedId,
        content: post.content,
        restaurantId: post.restaurantId,
        restaurantName: post.restaurantName,
        imageUrl: post.imageUrl,
        returnUrl: `${window.location.pathname}${window.location.search}`,
      }),
    );

    setOpenMenuFeedId(null);
    router.push(`/feed/write?edit=${post.feedId}`);
  };

  const handleDeleteFeed = async (feedId: number) => {
    const confirmed = window.confirm("피드를 삭제하시겠습니까?");
    if (!confirmed) return;

    const res = await apiFetchJson(`/api/v1/feeds/${feedId}`, {
      method: "DELETE",
    });

    if (res.ok) {
      removePostEverywhere(feedId);
      setOpenMenuFeedId(null);
      window.dispatchEvent(new Event("follow-state-change"));
      window.dispatchEvent(new Event("feed-state-change"));
    } else {
      alert(res.message || "피드 삭제에 실패했습니다.");
    }
  };

  const handleTabChange = (tab: "following" | "recommended") => {
    setPage(0);
    setPosts([]);
    setRecommendCursor(null);
    setRecommendPool([]);
    router.replace(`/feed?tab=${tab}`, { scroll: false });
  };

  useEffect(() => {
    const handleScroll = () => {
      if (
        window.innerHeight + window.scrollY >=
          document.body.offsetHeight - 200 &&
        canLoadMore &&
        !loading &&
        !loadingMore
      ) {
        setPage((prev) => prev + 1);
      }
    };

    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, [canLoadMore, loading, loadingMore]);

  return (
    <AppShell
      leftSidebar={
        <div className="sticky top-28 space-y-5">
          <SidebarProfile />
          <SidebarCard title="추천 푸디">
            <div className="space-y-4">
              {displayedFoodies.length === 0 ? (
                <p className="text-sm text-muted">추천 푸디가 없습니다.</p>
              ) : (
                displayedFoodies.map((f) => (
                  <div
                    key={f.userId}
                    className="flex items-center justify-between group"
                  >
                    <Link
                      href={`/profile/${f.userId}`}
                      className="flex items-center gap-3"
                    >
                      <img
                        src={getImageUrl(f.profileImage) ?? "/default-profile.png"}
                        alt=""
                        className="h-10 w-10 rounded-full object-cover"
                      />
                      <div>
                        <p className="text-base font-bold text-ink group-hover:text-primary transition-colors">
                          {f.nickname}
                        </p>
                      </div>
                    </Link>
                    <button
                      onClick={() => handleFollow(f.userId)}
                      className="rounded-full bg-primary px-3.5 py-1.5 text-sm font-bold text-white hover:bg-primary-active transition-colors"
                    >
                      팔로우
                    </button>
                  </div>
                ))
              )}
            </div>
          </SidebarCard>
        </div>
      }
    >
      <div className="space-y-5">
        {/* Page header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <h2 className="text-xl font-bold text-ink">피드</h2>
            <div className="flex items-center rounded-lg bg-surface-soft p-1">
              <button
                onClick={() => handleTabChange("following")}
                className={`rounded-md px-3 py-1 text-sm font-semibold transition-all ${
                  activeTab === "following"
                    ? "bg-primary text-white shadow-sm"
                    : "text-muted hover:text-ink"
                }`}
              >
                팔로잉
              </button>
              <button
                onClick={() => handleTabChange("recommended")}
                className={`rounded-md px-3 py-1 text-sm font-semibold transition-all ${
                  activeTab === "recommended"
                    ? "bg-primary text-white shadow-sm"
                    : "text-muted hover:text-ink"
                }`}
              >
                추천
              </button>
            </div>
          </div>
          <Link
            href="/feed/write"
            className="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-1.5 text-sm font-bold text-white hover:bg-primary-active transition-colors"
          >
            <Plus className="h-4 w-4" />
            글쓰기
          </Link>
        </div>

        {/* Feed cards */}
        {loading ? (
          <div className="space-y-4">
            <div className="h-64 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
            <div className="h-64 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
          </div>
        ) : error ? (
          <p className="py-10 text-center text-sm text-red-500">{error}</p>
        ) : (
          <div className="space-y-4">
            {displayedPosts.map((post) => (
              <article
                key={post.feedId}
                id={`feed-${post.feedId}`}
                className="rounded-2xl bg-surface p-5 border border-hairline-soft shadow-sm scroll-mt-20"
              >
                {/* Author */}
                <div className="flex items-center justify-between">
                  <Link
                    href={`/profile/${post.userId}`}
                    className="flex items-center gap-3 group"
                  >
                    <img
                      src={getImageUrl(post.profileImage) ?? "/default-profile.png"}
                      alt=""
                      className="h-10 w-10 rounded-full object-cover ring-1 ring-hairline-soft"
                    />
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-bold text-ink group-hover:text-primary transition-colors">
                          {post.nickname}
                        </p>
                        <span className="text-xs text-primary font-semibold">
                          {activeTab === "following" ? "팔로잉" : "추천"}
                        </span>
                      </div>
                      <p className="text-xs text-muted-soft">
                        {new Date(post.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </Link>
                  {currentUserId === post.userId && (
                    <div className="relative">
                      <button
                        type="button"
                        onClick={() =>
                          setOpenMenuFeedId((prev) =>
                            prev === post.feedId ? null : post.feedId,
                          )
                        }
                        className="rounded-full p-1.5 text-muted hover:bg-surface-soft"
                        aria-label="피드 메뉴"
                      >
                        <MoreHorizontal className="h-4 w-4" />
                      </button>

                      {openMenuFeedId === post.feedId && (
                        <div className="absolute right-0 top-8 z-20 w-28 overflow-hidden rounded-lg border border-hairline-soft bg-surface shadow-lg">
                          <button
                            type="button"
                            onClick={() => handleEditFeed(post)}
                            className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-ink hover:bg-surface-soft"
                          >
                            <Pencil className="h-3.5 w-3.5" />
                            수정
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteFeed(post.feedId)}
                            className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-500 hover:bg-surface-soft"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            삭제
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Content */}
                <p className="mt-4 text-sm leading-relaxed text-body">
                  {post.content}
                </p>

                {/* Feed image */}
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

                {/* Restaurant */}
                {post.restaurantId && post.restaurantName && (
                  <div className="mt-3">
                    <Link
                      href={`/restaurant/${post.restaurantId}`}
                      className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary hover:bg-primary/20 transition-colors"
                    >
                      🍴 {post.restaurantName}
                    </Link>
                  </div>
                )}

                {/* Actions */}
                <div className="mt-4 flex items-center gap-5 border-t border-hairline-soft pt-3">
                  <button
                    onClick={() =>
                      handleLikeToggle(post.feedId, post.isLikedByMe)
                    }
                    className={`flex items-center gap-1.5 text-sm transition-colors ${
                      post.isLikedByMe
                        ? "text-red-500"
                        : "text-muted hover:text-primary"
                    }`}
                  >
                    <Heart
                      className={`h-4 w-4 ${post.isLikedByMe ? "fill-current" : ""}`}
                    />
                    <span>좋아요 {post.likeCount}</span>
                  </button>
                  <button
                    onClick={() => handleOpenComments(post.feedId)}
                    className="flex items-center gap-1.5 text-sm text-muted hover:text-primary transition-colors"
                  >
                    <MessageCircle className="h-4 w-4" />
                    <span>댓글 {post.commentCount}</span>
                  </button>
                </div>
              </article>
            ))}
            {loadingMore && (
              <div className="py-4 text-center text-sm text-muted">
                불러오는 중...
              </div>
            )}
            {!loadingMore &&
              !canLoadMore &&
              activeTab === "recommended" &&
              displayedPosts.length > 0 && (
                <div className="flex flex-col items-center gap-2 py-6">
                  <p className="text-sm text-muted">
                    새로운 추천을 더 받아볼까요?
                  </p>
                  <button
                    onClick={handleRefreshRecommend}
                    className="flex items-center gap-1.5 rounded-full border border-hairline bg-surface px-4 py-2 text-sm font-bold text-ink hover:bg-surface-soft transition-colors"
                  >
                    <RefreshCw className="h-4 w-4" />
                    새로고침
                  </button>
                </div>
              )}
          </div>
        )}
      </div>
      {commentModalOpen && activeCommentFeedId !== null && (
        <CommentModal
          feedId={activeCommentFeedId}
          onClose={handleCloseComments}
          onCountChange={(count) =>
            handleCommentCountChange(activeCommentFeedId, count)
          }
        />
      )}
    </AppShell>
  );
}

export default function FeedPage() {
  return (
    <Suspense
      fallback={
        <AppShell>
          <div className="space-y-5">
            <div className="h-10 w-40 rounded-lg bg-surface-soft animate-pulse" />
            <div className="space-y-4">
              <div className="h-64 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
              <div className="h-64 rounded-2xl bg-surface border border-hairline-soft animate-pulse" />
            </div>
          </div>
        </AppShell>
      }
    >
      <FeedContent />
    </Suspense>
  );
}
