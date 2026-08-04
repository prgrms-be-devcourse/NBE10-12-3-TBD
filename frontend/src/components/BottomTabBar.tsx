"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Map, List, Sparkles, User } from "lucide-react";

/*
 * AppShell의 상단 네비(mainNav)와 동일한 5개 항목.
 * 순서·아이콘·라벨을 상단 네비와 일치시킨다.
 */
const tabs = [
  { href: "/feed", label: "피드", icon: Home },
  { href: "/search", label: "탐색", icon: Map },
  { href: "/lists", label: "리스트", icon: List },
  { href: "/recommend", label: "추천", icon: Sparkles },
  { href: "/profile", label: "프로필", icon: User },
];

export default function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-hairline-soft bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden">
      <div className="mx-auto flex h-16 max-w-lg items-stretch justify-around">
        {tabs.map((tab) => {
          const active =
            pathname === tab.href || pathname.startsWith(`${tab.href}/`);
          const Icon = tab.icon;

          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={`flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-semibold transition-colors ${
                active ? "text-primary" : "text-muted hover:text-ink"
              }`}
            >
              <Icon className="h-5 w-5" />
              {tab.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
