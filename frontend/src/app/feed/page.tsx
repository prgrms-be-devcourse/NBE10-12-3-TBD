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

function FeedContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const activeTab =
    searchParams.get("tab") === "recommended" ? "recommended" : "following";

  const [posts, setPosts] = useState<Feed[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
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

  const handleOpenComments = (feedId: number) => {
    setActiveCommentFeedId(feedId);
    setCommentModalOpen(true);
  };

  const handleCloseComments = () => {
    setCommentModalOpen(false);
    setActiveCommentFeedId(null);
  };

  const handleCommentCountChange = (feedId: number, count: number) => {
    setPosts((prev) =>
      prev.map((post) =>
        post.feedId === feedId ? { ...post, commentCount: count } : post,
      ),
    );
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

  useEffect(() => {
    const load = async () => {
      if (page === 0) {
        setLoading(true);
      } else {
        setLoadingMore(true);
      }
      setError("");

      const endpoint =
        activeTab === "following"
          ? "/api/v1/feeds/following"
          : "/api/v1/feeds/recommend";
      const res = await apiFetchJson<FeedListPageResponse>(
        `${endpoint}?page=${page}&size=20`,
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

  useEffect(() => {
    if (!currentUserId) return;

    const loadFoodies = async () => {
      const res = await apiFetchJson<FeedListPageResponse>(
        "/api/v1/feeds/recommend",
      );
      if (!res.ok || !res.data?.feeds) return;

      const seen = new Set<number>();
      const unique: RecommendFoodie[] = [];

      for (const feed of res.data.feeds) {
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

      setRecommendFoodies(unique);
    };

    loadFoodies();
  }, [currentUserId]);

  const handleFollow = async (userId: number) => {
    const res = await apiFetchJson(`/api/v1/follows/${userId}`, {
      method: "POST",
    });

    if (res.ok) {
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
      setPosts((prev) =>
        prev.map((post) =>
          post.feedId === feedId
            ? {
                ...post,
                isLikedByMe: !currentlyLiked,
                likeCount: currentlyLiked
                  ? post.likeCount - 1
                  : post.likeCount + 1,
              }
            : post,
        ),
      );
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
      setPosts((prev) => prev.filter((post) => post.feedId !== feedId));
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
    router.replace(`/feed?tab=${tab}`, { scroll: false });
  };

  useEffect(() => {
    const handleScroll = () => {
      if (
        window.innerHeight + window.scrollY >=
          document.body.offsetHeight - 200 &&
        hasMore &&
        !loading &&
        !loadingMore
      ) {
        setPage((prev) => prev + 1);
      }
    };

    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, [hasMore, loading, loadingMore]);

  return (
    <AppShell
      leftSidebar={
        <div className="sticky top-28 space-y-5">
          <SidebarProfile />
          <SidebarCard title="추천 푸디">
            <div className="space-y-4">
              {recommendFoodies.length === 0 ? (
                <p className="text-sm text-muted">추천 푸디가 없습니다.</p>
              ) : (
                recommendFoodies.map((f) => (
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
            {posts.map((post) => (
              <article
                key={post.feedId}
                className="rounded-2xl bg-surface p-5 border border-hairline-soft shadow-sm"
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
