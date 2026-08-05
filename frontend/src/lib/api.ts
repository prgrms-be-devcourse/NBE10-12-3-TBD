export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

export function getImageUrl(url?: string | null): string | null {
  if (!url) return null;

  if (url.startsWith("/uploads/")) return url;

  try {
    const parsedUrl = new URL(url);
    if (parsedUrl.pathname.startsWith("/uploads/")) {
      return `${parsedUrl.pathname}${parsedUrl.search}`;
    }
  } catch {
    return url;
  }

  return url;
}

const protectedPathPrefixes = [
  "/feed",
  "/profile",
  "/search",
  "/recommend",
  "/lists",
  "/restaurant",
] as const;

function clearClientSession() {
  localStorage.removeItem("isLoggedIn");
  localStorage.removeItem("user");
  window.dispatchEvent(new Event("login-state-change"));
}

function shouldRedirectToLogin(status: number) {
  if (typeof window === "undefined" || status !== 401) return false;
  return protectedPathPrefixes.some((prefix) => window.location.pathname.startsWith(prefix));
}

export async function apiFetch(path: string, options?: RequestInit) {
  const isFormData = options?.body instanceof FormData;

  return fetch(`${API_BASE}${path}`, {
    credentials: "include",
    ...options,
    headers: {
      ...(options?.body && !isFormData ? { "Content-Type": "application/json" } : {}),
      ...options?.headers,
    },
  });
}

const AUTH_PATH_PREFIX = "/api/v1/auth/";

// accessToken(1시간)이 만료되면 매번 강제 로그아웃시키지 않고, refreshToken(7일) 쿠키로
// /auth/reissue를 한 번 시도한 뒤 원래 요청을 재시도한다. 동시에 여러 요청이 401을 맞아도
// reissue 호출은 하나만 나가도록 진행 중인 Promise를 공유한다.
let reissuePromise: Promise<boolean> | null = null;

async function reissueAccessToken(): Promise<boolean> {
  if (!reissuePromise) {
    reissuePromise = apiFetch(`${AUTH_PATH_PREFIX}reissue`, { method: "POST" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        reissuePromise = null;
      });
  }
  return reissuePromise;
}

export async function apiFetchJson<T = unknown>(
  path: string,
  options?: RequestInit,
  _retriedAfterReissue = false,
): Promise<{ ok: boolean; data?: T; message?: string }> {
  const res = await apiFetch(path, options);
  const json = await res.json().catch(() => ({}));

  if (!res.ok) {
    // /auth 엔드포인트 자체의 401(예: 로그인 실패)까지 reissue를 시도하면 무한 재귀가
    // 되므로 제외하고, 재시도 요청 자체가 또 401이 나면(refreshToken도 무효) 더 이상
    // 재시도하지 않고 아래 로그아웃 처리로 넘어간다.
    if (res.status === 401 && !_retriedAfterReissue && !path.startsWith(AUTH_PATH_PREFIX)) {
      const reissued = await reissueAccessToken();
      if (reissued) {
        return apiFetchJson<T>(path, options, true);
      }
    }

    if (shouldRedirectToLogin(res.status)) {
      clearClientSession();
      window.location.assign("/login");
    }

    return { ok: false, message: json.message || "요청에 실패했습니다." };
  }

  return { ok: true, data: json.data, message: json.message };
}
