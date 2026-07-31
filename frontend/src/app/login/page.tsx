"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { MessageCircle, Map, List, Sparkles } from "lucide-react";
import { apiFetchJson } from "@/lib/api";

type Tab = "kakao" | "email";
type Mode = "login" | "signup";

const API_BASE = "";
const OAUTH_BASE = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

interface HotPlace {
  id: number;
  name: string;
  category: string;
  region2: string;
}

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

const features = [
  { icon: Map, title: "위치 기반 맛집 탐색", desc: "주변의 진짜 맛집을 빠르게 찾아보세요" },
  { icon: List, title: "테마별 맛집 리스트", desc: "상황에 딱 맞는 큐레이션을 저장하세요" },
  { icon: MessageCircle, title: "팔로잉 기반 피드", desc: "신뢰하는 푸디들의 솔직한 후기" },
  { icon: Sparkles, title: "AI 맞춤 추천", desc: "분위기와 위치에 꼭 맞는 음식점 추천" },
];

export default function LoginPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<Tab>("kakao");
  const [mode, setMode] = useState<Mode>("login");
  const [loading, setLoading] = useState(false);
  const [nickname, setNickname] = useState("");
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [error, setError] = useState("");
  const [hotPlaces, setHotPlaces] = useState<HotPlace[]>([]);

  useEffect(() => {
    const loadHotPlaces = async () => {
      const res = await apiFetchJson<HotPlace[]>("/api/v1/restaurants/today-hot");
      if (res.ok && res.data) {
        setHotPlaces(res.data);
      }
    };

    loadHotPlaces();
  }, []);

  const handleLoginSuccess = (user: Record<string, unknown>) => {
    localStorage.setItem("isLoggedIn", "true");
    localStorage.setItem("user", JSON.stringify(user));
    window.dispatchEvent(new Event("login-state-change"));
    router.push("/feed");
  };

  const handleKakaoLogin = () => {
    setLoading(true);
    const redirectUri = encodeURIComponent(window.location.origin);
    window.location.href = `${OAUTH_BASE}/oauth2/authorization/kakao?redirectUri=${redirectUri}`;
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (mode === "signup" && password !== passwordConfirm) {
      setError("비밀번호가 일치하지 않습니다.");
      return;
    }

    setLoading(true);

    const url =
      mode === "login"
        ? `${API_BASE}/api/v1/auth/login`
        : `${API_BASE}/api/v1/auth/signup`;

    const body: Record<string, string> = { loginId, password };
    if (mode === "signup") {
      body.nickname = nickname;
    }

    try {
      const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body),
      });

      const result = await res.json().catch(() => ({}));

      if (res.ok && result.success) {
        handleLoginSuccess(result.data ?? {});
      } else {
        const message = result.message || "로그인/회원가입에 실패했습니다.";
        setError(message);
        alert(message);
      }
    } catch {
      const message = "서버와 통신 중 오류가 발생했습니다.";
      setError(message);
      alert(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      {/* Left: Branding */}
      <div className="relative hidden lg:flex flex-col justify-between bg-primary p-10 text-white overflow-hidden">
        <div className="relative z-10">
          <h1 className="text-3xl font-black tracking-tight">오늘뭐먹지</h1>
          <p className="mt-2 text-white/90 font-medium">신뢰할 수 있는 푸디들의 맛집 큐레이션</p>
        </div>

        <div className="relative z-10 space-y-8">
          <div className="space-y-4">
            <div className="flex items-start gap-3">
              <Map className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm font-medium">위치 기반 맛집 지도 및 검색</p>
            </div>
            <div className="flex items-start gap-3">
              <List className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm font-medium">테마별 맛집 리스트 큐레이션</p>
            </div>
            <div className="flex items-start gap-3">
              <MessageCircle className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm font-medium">팔로잉 기반 소셜 피드</p>
            </div>
            <div className="flex items-start gap-3">
              <Sparkles className="h-5 w-5 mt-0.5 shrink-0" />
              <p className="text-sm font-medium">AI 추천 맞춤 맛집 추천</p>
            </div>
          </div>

          <div>
            <p className="text-sm font-bold mb-3">지금 핫한 맛집</p>
            <div className="flex gap-3 overflow-x-auto pb-2">
              {hotPlaces.length === 0 ? (
                <p className="text-xs text-white/80">등록된 식당이 없습니다.</p>
              ) : (
                hotPlaces.map((p) => (
                  <Link
                    key={p.id}
                    href={`/restaurant/${p.id}`}
                    className="min-w-[160px] rounded-xl bg-white/15 p-3 backdrop-blur-sm transition-colors hover:bg-white/25"
                  >
                    <div className="h-20 rounded-lg bg-white/20 overflow-hidden mb-2">
                      <img src="/restaurant-placeholder.png" alt="" className="h-full w-full object-cover" />
                    </div>
                    <p className="text-sm font-bold truncate">{p.name}</p>
                    <p className="text-xs text-white/80">{categoryLabelMap[p.category] || p.category} · {p.region2 || "-"}</p>
                  </Link>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="relative z-10 text-xs text-white/60">
          © 2025 오늘뭐먹지
        </div>

        <div className="absolute inset-0 bg-gradient-to-br from-primary via-primary to-primary-active opacity-90" />
        <div className="absolute -bottom-32 -left-32 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -top-32 -right-32 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
      </div>

      {/* Right: Login */}
      <div className="flex flex-col justify-center px-6 py-12 lg:px-16 bg-background">
        <div className="mx-auto w-full max-w-md">
          <div className="lg:hidden mb-8 text-center">
            <h1 className="text-2xl font-black text-primary">오늘뭐먹지</h1>
            <p className="text-sm text-muted mt-1">신뢰할 수 있는 푸디들의 맛집 큐레이션</p>
          </div>

          <h2 className="text-2xl font-bold text-ink">시작하기</h2>
          <p className="mt-1 text-sm text-muted">계정으로 간편하게 로그인하세요</p>

          {/* Tabs */}
          <div className="mt-6 flex rounded-xl bg-surface-soft p-1">
            <button
              onClick={() => setActiveTab("kakao")}
              className={`flex-1 rounded-lg py-2 text-sm font-bold transition-all ${
                activeTab === "kakao" ? "bg-surface text-ink shadow-sm" : "text-muted hover:text-ink"
              }`}
            >
              카카오 로그인
            </button>
            <button
              onClick={() => setActiveTab("email")}
              className={`flex-1 rounded-lg py-2 text-sm font-bold transition-all ${
                activeTab === "email" ? "bg-surface text-ink shadow-sm" : "text-muted hover:text-ink"
              }`}
            >
              이메일 로그인
            </button>
          </div>

          {activeTab === "kakao" ? (
            <div className="mt-6 space-y-4">
              <button
                onClick={handleKakaoLogin}
                disabled={loading}
                className="block w-full overflow-hidden rounded-xl transition-transform active:scale-[0.98] disabled:opacity-80"
              >
                <img
                  src="/kakao_login/kakao_login_large_wide.png"
                  alt="카카오로 계속하기"
                  className="h-auto w-full"
                />
              </button>
              <p className="text-center text-xs text-muted">
                로그인하면 <button className="text-primary hover:underline">서비스 이용약관</button>과{" "}
                <button className="text-primary hover:underline">개인정보 처리방침</button>에 동의하게 됩니다.
              </p>
            </div>
          ) : (
            <form onSubmit={handleEmailSubmit} className="mt-6 space-y-4">
              {mode === "signup" && (
                <div>
                  <label className="text-xs font-bold text-muted">닉네임</label>
                  <input
                    type="text"
                    placeholder="푸디 닉네임"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm focus:border-primary focus:outline-hidden"
                    required
                  />
                </div>
              )}
              <div>
                <label className="text-xs font-bold text-muted">이메일</label>
                <input
                  type="email"
                  placeholder="email@example.com"
                  value={loginId}
                  onChange={(e) => setLoginId(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm focus:border-primary focus:outline-hidden"
                  required
                />
              </div>
              <div>
                <label className="text-xs font-bold text-muted">비밀번호</label>
                <input
                  type="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm focus:border-primary focus:outline-hidden"
                  required
                />
              </div>
              {mode === "signup" && (
                <div>
                  <label className="text-xs font-bold text-muted">비밀번호 확인</label>
                  <input
                    type="password"
                    placeholder="••••••••"
                    value={passwordConfirm}
                    onChange={(e) => setPasswordConfirm(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-hairline bg-surface-soft px-4 py-2.5 text-sm focus:border-primary focus:outline-hidden"
                    required
                  />
                </div>
              )}
              {error && <p className="text-center text-sm text-red-500">{error}</p>}
              <button
                type="submit"
                disabled={loading}
                className="w-full rounded-xl bg-primary py-3 text-sm font-bold text-white hover:bg-primary-active transition-colors disabled:opacity-70"
              >
                {loading ? "처리 중..." : mode === "login" ? "로그인" : "회원가입"}
              </button>
              <p className="text-center text-sm text-muted">
                {mode === "login" ? "아직 계정이 없으신가요?" : "이미 계정이 있으신가요?"}{" "}
                <button
                  type="button"
                  onClick={() => {
                    setMode(mode === "login" ? "signup" : "login");
                    setError("");
                  }}
                  className="font-bold text-primary hover:underline"
                >
                  {mode === "login" ? "회원가입" : "로그인"}
                </button>
              </p>
            </form>
          )}

          {/* Features grid */}
          <div className="mt-12">
            <p className="text-sm font-bold text-muted mb-4 text-center">오늘뭐먹지의 특별한 점</p>
            <div className="grid grid-cols-2 gap-3">
              {features.map((f) => (
                <div key={f.title} className="rounded-xl bg-surface p-4 border border-hairline-soft">
                  <f.icon className="h-5 w-5 text-primary mb-2" />
                  <p className="text-sm font-bold text-ink">{f.title}</p>
                  <p className="mt-1 text-xs text-muted leading-relaxed">{f.desc}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="mt-8 flex justify-center gap-4 text-xs text-muted-soft">
            <button className="hover:text-muted">이용약관</button>
            <button className="hover:text-muted">개인정보처리방침</button>
            <button className="hover:text-muted">고객센터</button>
          </div>
        </div>
      </div>
    </div>
  );
}
