"use client";

import { ReactNode, Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Home,
  Map,
  List,
  Sparkles,
  User,
  Search,
  LogOut,
  Settings,
  ArrowUp,
} from "lucide-react";
import { CurrentUser, getStoredUser, setStoredUser } from "@/lib/user";

import { apiFetchJson, getImageUrl } from "@/lib/api";
import { NotificationBell } from "@/components/NotificationBell";
import BottomTabBar from "@/components/BottomTabBar";

/* =========================================================
 * 사용자 응답
 * ========================================================= */

interface UserProfileResponse {
  id: number;
  nickname: string;
  profileImage: string | null;
  email: string;
}

/* =========================================================
 * AppShell Props
 *
 * fullWidth:
 * true일 경우 오른쪽 사이드바가 없으면 빈 300px 컬럼을 만들지 않음
 * ========================================================= */

interface AppShellProps {
  children: ReactNode;
  leftSidebar?: ReactNode;
  rightSidebar?: ReactNode;
  hideSidebars?: boolean;
  fullWidth?: boolean;
}

/* =========================================================
 * 메인 네비게이션
 * ========================================================= */

const mainNav = [
  {
    href: "/feed",
    label: "피드",
    icon: Home,
  },
  {
    href: "/search",
    label: "탐색",
    icon: Map,
  },
  {
    href: "/lists",
    label: "리스트",
    icon: List,
  },
  {
    href: "/recommend",
    label: "추천",
    icon: Sparkles,
  },
  {
    href: "/profile",
    label: "프로필",
    icon: User,
  },
];

/* =========================================================
 * 기본 사용자
 * ========================================================= */

const fallbackUser: CurrentUser = {
  userId: 0,
  nickname: "게스트",
  profileImage: "/default-profile.png",
  email: "",
};

interface FeedListPageResponse {
  feeds: unknown[];
}

/* =========================================================
 * 왼쪽 프로필
 * ========================================================= */

export function SidebarProfile() {
  const [user, setUser] = useState<CurrentUser>(
    () => getStoredUser() ?? fallbackUser,
  );

  const [mounted, setMounted] = useState(false);

  const [followingCount, setFollowingCount] = useState<number | null>(null);

  const [followerCount, setFollowerCount] = useState<number | null>(null);

  const [postCount, setPostCount] = useState<number | null>(null);

  /* ---------------------------------------------------------
   * 마운트 확인
   * --------------------------------------------------------- */

  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      setMounted(true);
    });

    return () => {
      cancelAnimationFrame(raf);
    };
  }, []);

  /* ---------------------------------------------------------
   * 로그인 상태 변경
   * --------------------------------------------------------- */

  useEffect(() => {
    const handleChange = () => {
      setUser(getStoredUser() ?? fallbackUser);
    };

    window.addEventListener("login-state-change", handleChange);

    return () => {
      window.removeEventListener("login-state-change", handleChange);
    };
  }, []);

  /* ---------------------------------------------------------
   * 프로필 통계 조회
   * --------------------------------------------------------- */

  useEffect(() => {
    if (!user.userId) {
      return;
    }

    const loadCounts = async () => {
      const [countRes, feedRes] = await Promise.all([
        apiFetchJson<{
          userId: number;
          followerCount: number;
          followingCount: number;
        }>(`/api/v1/follows/users/${user.userId}/count`),
        apiFetchJson<FeedListPageResponse>(
          `/api/v1/feeds?userId=${user.userId}`,
        ),
      ]);

      if (countRes.ok && countRes.data) {
        setFollowerCount(countRes.data.followerCount);

        setFollowingCount(countRes.data.followingCount);
      }

      if (feedRes.ok && feedRes.data) {
        setPostCount(feedRes.data.feeds.length);
      }
    };

    loadCounts();

    const handleFollowStateChange = () => {
      loadCounts();
    };

    window.addEventListener("follow-state-change", handleFollowStateChange);
    window.addEventListener("feed-state-change", handleFollowStateChange);

    return () => {
      window.removeEventListener(
        "follow-state-change",
        handleFollowStateChange,
      );
      window.removeEventListener("feed-state-change", handleFollowStateChange);
    };
  }, [user.userId]);

  /* ---------------------------------------------------------
   * 마운트 전 스켈레톤
   * --------------------------------------------------------- */

  if (!mounted) {
    return (
      <div className="block rounded-2xl border border-hairline-soft bg-surface p-5">
        <div className="flex items-center gap-4">
          <div className="h-14 w-14 animate-pulse rounded-full bg-surface-strong ring-2 ring-primary/20" />

          <div className="space-y-2">
            <div className="h-4 w-24 animate-pulse rounded bg-surface-strong" />

            <div className="h-3 w-32 animate-pulse rounded bg-surface-strong" />
          </div>
        </div>

        <div className="mt-4 flex justify-between text-center text-base">
          <div>
            <p className="font-bold text-ink">-</p>

            <p className="text-xs text-muted">팔로잉</p>
          </div>

          <div>
            <p className="font-bold text-ink">-</p>

            <p className="text-xs text-muted">팔로워</p>
          </div>

          <div>
            <p className="font-bold text-ink">-</p>

            <p className="text-xs text-muted">포스트</p>
          </div>
        </div>
      </div>
    );
  }

  /* ---------------------------------------------------------
   * 프로필
   * --------------------------------------------------------- */

  return (
    <Link
      href="/profile"
      className="block rounded-2xl bg-surface p-5 border border-hairline-soft hover:border-primary/30 transition-colors"
    >
      <div className="flex items-center gap-4">
        <img
          src={getImageUrl(user.profileImage) ?? "/default-profile.png"}
          alt=""
          className="h-14 w-14 rounded-full object-cover ring-2 ring-primary/20"
        />

        <div className="min-w-0">
          <p className="truncate text-base font-bold text-ink">
            {user.nickname}
          </p>

          <p className="truncate text-sm text-muted">
            {user.email || "로그인이 필요합니다"}
          </p>
        </div>
      </div>

      <div className="mt-4 flex justify-between text-center text-base">
        <div>
          <p className="font-bold text-ink">{followingCount ?? "-"}</p>

          <p className="text-xs text-muted">팔로잉</p>
        </div>

        <div>
          <p className="font-bold text-ink">{followerCount ?? "-"}</p>

          <p className="text-xs text-muted">팔로워</p>
        </div>

        <div>
          <p className="font-bold text-ink">{postCount ?? "-"}</p>

          <p className="text-xs text-muted">포스트</p>
        </div>
      </div>
    </Link>
  );
}

/* =========================================================
 * 사이드바 카드
 * ========================================================= */

export function SidebarCard({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-hairline-soft bg-surface p-5">
      <p className="mb-4 text-base font-bold text-ink">{title}</p>

      {children}
    </div>
  );
}

/* =========================================================
 * 기본 왼쪽 사이드바
 * ========================================================= */

function DefaultLeftSidebar() {
  return (
    <div className="sticky top-28 space-y-5">
      <SidebarProfile />
    </div>
  );
}

/* =========================================================
 * AppShell
 * ========================================================= */

export default function AppShell({
  children,
  leftSidebar,
  rightSidebar,
  hideSidebars = false,
  fullWidth = false,
}: AppShellProps) {
  const router = useRouter();

  const pathname = usePathname();

  const [menuOpen, setMenuOpen] = useState(false);
  const [user, setUser] = useState<CurrentUser>(
    () => getStoredUser() ?? fallbackUser,
  );

  const [mounted, setMounted] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const menuRef = useRef<HTMLDivElement>(null);

  const [showBackToTop, setShowBackToTop] = useState(false);

  /* ---------------------------------------------------------
   * 위로가기 버튼 표시 여부
   * --------------------------------------------------------- */

  useEffect(() => {
    const handleScroll = () => {
      setShowBackToTop(window.scrollY > 400);
    };

    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      window.removeEventListener("scroll", handleScroll);
    };
  }, []);

  /* =========================================================
   * 로그인 상태 변경
   * ========================================================= */

  useEffect(() => {
    const handleChange = () => {
      setUser(getStoredUser() ?? fallbackUser);
    };

    window.addEventListener("login-state-change", handleChange);

    return () => {
      window.removeEventListener("login-state-change", handleChange);
    };
  }, []);

  /* =========================================================
   * 마운트 확인
   * ========================================================= */

  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      setMounted(true);
    });

    return () => {
      cancelAnimationFrame(raf);
    };
  }, []);

  /* =========================================================
   * 로그인 사용자 조회
   * ========================================================= */

  useEffect(() => {
    const loadMe = async () => {
      const res = await apiFetchJson<UserProfileResponse>("/api/v1/users/me");

      if (res.ok && res.data) {
        const currentUser: CurrentUser = {
          userId: res.data.id,
          nickname: res.data.nickname,
          profileImage: res.data.profileImage,
          email: res.data.email,
        };

        setStoredUser(currentUser);

        setUser(currentUser);

        window.dispatchEvent(new Event("login-state-change"));
      }
    };

    loadMe();
  }, []);

  /* =========================================================
   * 프로필 메뉴 바깥 클릭
   * ========================================================= */

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };

    if (menuOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [menuOpen]);

  /* =========================================================
   * 로그아웃
   * ========================================================= */

  const handleLogout = async () => {
    try {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch {
      /* 서버 요청 실패 시에도 로컬 정보 제거 */
    } finally {
      localStorage.removeItem("isLoggedIn");

      localStorage.removeItem("user");

      window.dispatchEvent(new Event("login-state-change"));

      router.push("/login");
    }
  };

  /* =========================================================
   * 사이드바 없는 화면
   * ========================================================= */

  if (hideSidebars) {
    return <main className="min-h-screen bg-background">{children}</main>;
  }

  /* =========================================================
   * 메인 레이아웃 클래스
   *
   * 일반 화면:
   * 왼쪽 300 / 메인 / 오른쪽 300
   *
   * fullWidth + rightSidebar 없음:
   * 왼쪽 300 / 나머지 전체
   * ========================================================= */

  const contentGridClass = fullWidth
    ? rightSidebar
      ? "xl:grid-cols-[300px_minmax(0,1fr)_300px]"
      : "xl:grid-cols-[300px_minmax(0,1fr)]"
    : "xl:grid-cols-[300px_minmax(0,1fr)_300px]";

  /* =========================================================
   * 화면
   * ========================================================= */

  return (
    <div className="min-h-screen bg-background">
      {/* =====================================================
       * 헤더
       * ===================================================== */}

      <header className="sticky top-0 z-30 border-b border-hairline-soft bg-surface/95 backdrop-blur">
        <div className="mx-auto flex h-16 w-full items-center px-4 md:h-[100px] lg:px-8 xl:max-w-[calc(100vw-200px)] xl:px-0">
          {/* 왼쪽 */}

          <div className="flex items-center gap-8">
            <Link
              href="/feed"
              className="text-2xl font-bold tracking-tight text-primary"
            >
              오늘뭐먹지
            </Link>

            {mounted && (
              <div className="hidden items-center rounded-full bg-surface-soft px-4 py-2 md:flex">
                <Search className="h-5 w-5 text-muted" />

                <input
                  type="text"
                  placeholder="맛집, 지역 검색..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && searchQuery.trim()) {
                      router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
                    }
                  }}
                  className="ml-3 w-40 bg-transparent text-base text-ink placeholder:text-muted-soft focus:outline-hidden xl:w-56"
                  suppressHydrationWarning
                />
              </div>
            )}
          </div>

          {/* 네비게이션 */}

          <nav className="ml-auto hidden items-center justify-end gap-2 lg:flex">
            {mainNav.map((item) => {
              const active =
                pathname === item.href || pathname.startsWith(`${item.href}/`);

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`rounded-lg px-4 py-2 text-base font-semibold transition-all ${
                    active
                      ? "bg-primary text-white shadow-sm"
                      : "text-muted hover:bg-surface-soft hover:text-ink"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          {/* 알림 / 프로필 */}

          <div className="ml-auto flex items-center gap-3 lg:ml-8">
            <NotificationBell />

            <div className="relative" ref={menuRef}>
              <button
                type="button"
                onClick={() => setMenuOpen((prev) => !prev)}
                className="flex h-10 w-10 items-center justify-center overflow-hidden rounded-full bg-primary/10 ring-2 ring-primary/20 focus:outline-hidden"
              >
                {mounted ? (
                  <img
                    src={getImageUrl(user.profileImage) ?? "/default-profile.png"}
                    alt="프로필"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <User className="h-5 w-5 text-muted" />
                )}
              </button>

              {menuOpen && (
                <div className="absolute right-0 mt-2 w-48 animate-in rounded-xl border border-hairline-soft bg-surface py-2 shadow-lg fade-in-50 zoom-in-95">
                  <Link
                    href="/profile"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-3 px-4 py-2.5 text-base font-semibold text-ink transition-colors hover:bg-surface-soft"
                  >
                    <User className="h-5 w-5 text-muted" />
                    마이페이지
                  </Link>

                  <Link
                    href="/profile/edit"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-3 px-4 py-2.5 text-base font-semibold text-ink transition-colors hover:bg-surface-soft"
                  >
                    <Settings className="h-5 w-5 text-muted" />내 정보 수정
                  </Link>

                  <div className="my-1 border-t border-hairline-soft" />

                  <button
                    type="button"
                    onClick={handleLogout}
                    className="flex w-full items-center gap-3 px-4 py-2.5 text-base font-semibold text-red-500 transition-colors hover:bg-red-50"
                  >
                    <LogOut className="h-5 w-5" />
                    로그아웃
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* =====================================================
       * 본문
       * ===================================================== */}

      <div
        className={`mx-auto grid grid-cols-1 gap-5 px-4 py-6 pb-24 lg:pb-6 ${contentGridClass} ${
          fullWidth
            ? "w-full max-w-none px-4 md:px-6 lg:px-10"
            : "w-full lg:px-8 xl:max-w-[calc(100vw-200px)] xl:px-0"
        }`}
      >
        {/* =================================================
         * 왼쪽 사이드바
         * ================================================= */}

        <aside className="hidden xl:block">
          <Suspense
            fallback={
              <div className="sticky top-28 space-y-5">
                <div className="h-40 animate-pulse rounded-2xl border border-hairline-soft bg-surface" />

                <div className="h-56 animate-pulse rounded-2xl border border-hairline-soft bg-surface" />
              </div>
            }
          >
            {leftSidebar ?? <DefaultLeftSidebar />}
          </Suspense>
        </aside>

        {/* =================================================
         * 메인
         * ================================================= */}

        <main className="min-w-0 w-full">{children}</main>

        {/* =================================================
         * 오른쪽 사이드바
         *
         * fullWidth이며 rightSidebar가 없으면
         * 아예 렌더링하지 않음
         * ================================================= */}

        {(!fullWidth || rightSidebar) && (
          <aside className="hidden xl:block">
            <div className="sticky top-28">{rightSidebar}</div>
          </aside>
        )}
      </div>

      {/* 하단 탭바 (lg 미만) */}
      <BottomTabBar />

      {/* 위로가기 버튼 (스크롤 시 표시, 모바일은 탭바 위) */}
      {showBackToTop && (
        <button
          type="button"
          onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          aria-label="맨 위로"
          className="fixed bottom-20 right-4 z-30 flex h-11 w-11 items-center justify-center rounded-full border border-hairline-soft bg-surface/80 text-ink shadow-lg backdrop-blur transition-colors hover:bg-surface lg:bottom-6"
        >
          <ArrowUp className="h-5 w-5" />
        </button>
      )}
    </div>
  );
}
