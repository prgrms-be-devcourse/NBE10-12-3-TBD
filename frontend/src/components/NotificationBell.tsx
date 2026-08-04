"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell } from "lucide-react";
import { apiFetchJson } from "@/lib/api";
import { buildListHref } from "@/lib/listNavigation";

type NotificationType = "NEW_FEED" | "FEED_LIKE" | "FEED_COMMENT" | "FOLLOW" | "LIST_SHARE";

interface NotificationItem {
  id: number;
  actorId: number;
  actorNickname: string;
  feedId: number | null;
  restaurantListId: number | null;
  type: NotificationType;
  message: string;
  isRead: boolean;
  createdAt: string;
}

interface NotificationCursorResponse {
  content: NotificationItem[];
  nextCursor: number | null;
  hasNext: boolean;
}

function formatCreatedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString();
}

export function NotificationBell() {
  const router = useRouter();
  const menuRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);

  const unreadCount = notifications.filter((notification) => !notification.isRead).length;

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    const res = await apiFetchJson<NotificationCursorResponse>("/api/v1/notifications");
    if (res.ok && res.data) {
      setNotifications(res.data.content ?? []);
      setNextCursor(res.data.nextCursor);
      setHasNext(res.data.hasNext);
    }
    setLoading(false);
  }, []);

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasNext || nextCursor == null) return;
    setLoadingMore(true);
    const res = await apiFetchJson<NotificationCursorResponse>(
      `/api/v1/notifications?cursor=${nextCursor}`
    );
    if (res.ok && res.data) {
      setNotifications((prev) => [...prev, ...(res.data?.content ?? [])]);
      setNextCursor(res.data.nextCursor);
      setHasNext(res.data.hasNext);
    }
    setLoadingMore(false);
  }, [loadingMore, hasNext, nextCursor]);

  const handleScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const target = event.currentTarget;
    const nearBottom =
      target.scrollHeight - target.scrollTop - target.clientHeight < 80;
    if (nearBottom) void loadMore();
  };

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      void loadNotifications();
    });

    return () => cancelAnimationFrame(frame);
  }, [loadNotifications]);

  useEffect(() => {
    if (open) {
      const frame = requestAnimationFrame(() => {
        void loadNotifications();
      });

      return () => cancelAnimationFrame(frame);
    }
  }, [loadNotifications, open]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (event.target instanceof Node && menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false);
      }
    };

    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
    }

    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const handleNotificationClick = async (notification: NotificationItem) => {
    if (!notification.isRead) {
      const res = await apiFetchJson<NotificationItem>(
        `/api/v1/notifications/${notification.id}/read`,
        { method: "PUT" }
      );

      if (res.ok && res.data) {
        const updatedNotification = res.data;
        setNotifications((prev) =>
          prev.map((item) => (item.id === notification.id ? updatedNotification : item))
        );
      }
    }

    setOpen(false);
    switch (notification.type) {
      case "NEW_FEED":
        // 팔로우한 유저의 새 글 → 팔로잉 탭
        if (notification.feedId) {
          router.push(`/feed?tab=following&highlight=${notification.feedId}`);
        }
        break;
      case "FEED_LIKE":
      case "FEED_COMMENT":
        // 내 피드에 대한 반응 → 내 피드는 팔로잉 탭에 안 뜨므로 추천 탭으로
        if (notification.feedId) {
          router.push(`/feed?tab=recommended&highlight=${notification.feedId}`);
        }
        break;
      case "FOLLOW":
        if (notification.actorId) router.push(`/profile/${notification.actorId}`);
        break;
      case "LIST_SHARE":
        if (notification.restaurantListId) {
          router.push(buildListHref(notification.restaurantListId, "other"));
        }
        break;
    }
  };

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setOpen((prev) => !prev)}
        className="relative rounded-full p-2.5 text-muted transition-colors hover:bg-surface-soft hover:text-ink"
        aria-label="알림"
      >
        <Bell className="h-6 w-6" />
        {unreadCount > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold leading-none text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-[calc(100vw-2rem)] max-w-80 overflow-hidden rounded-xl border border-hairline-soft bg-surface shadow-lg animate-in fade-in-50 zoom-in-95">
          <div className="flex items-center justify-between border-b border-hairline-soft px-4 py-3">
            <p className="text-sm font-bold text-ink">알림</p>
            <span className="text-xs font-semibold text-muted">읽지 않음 {unreadCount}</span>
          </div>

          <div className="max-h-96 overflow-y-auto py-2" onScroll={handleScroll}>
            {loading ? (
              <p className="px-4 py-8 text-center text-sm text-muted">알림을 불러오는 중입니다.</p>
            ) : notifications.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-muted">새 알림이 없습니다.</p>
            ) : (
              <>
                {notifications.map((notification) => (
                  <button
                    key={notification.id}
                    type="button"
                    onClick={() => handleNotificationClick(notification)}
                    className="flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-soft"
                  >
                    <span
                      className={`mt-1 h-2 w-2 shrink-0 rounded-full ${
                        notification.isRead ? "bg-hairline" : "bg-primary"
                      }`}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold text-ink">
                        {notification.actorNickname}
                      </span>
                      <span className="mt-0.5 block text-sm leading-5 text-body">
                        {notification.message}
                      </span>
                      <span className="mt-1 block text-xs text-muted-soft">
                        {formatCreatedAt(notification.createdAt)}
                      </span>
                    </span>
                  </button>
                ))}
                {loadingMore && (
                  <p className="px-4 py-3 text-center text-xs text-muted">더 불러오는 중...</p>
                )}
                {!hasNext && !loadingMore && (
                  <p className="px-4 py-3 text-center text-xs text-muted-soft">모든 알림을 확인했어요</p>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
