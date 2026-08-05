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

const AUTH_PATH_PREFIX = "/api/v1/auth/";

function buildRequestInit(options?: RequestInit): RequestInit {
  const isFormData = options?.body instanceof FormData;

  return {
    credentials: "include",
    ...options,
    headers: {
      ...(options?.body && !isFormData ? { "Content-Type": "application/json" } : {}),
      ...options?.headers,
    },
  };
}

// accessToken(1시간)이 만료되면 매번 강제 로그아웃시키지 않고, refreshToken(7일) 쿠키로
// /auth/reissue를 한 번 시도한 뒤 원래 요청을 재시도한다. 동시에 여러 요청이 401을 맞아도
// reissue 호출은 하나만 나가도록 진행 중인 Promise를 공유한다. rawFetch는 apiFetch를 거치지
// 않는 순수 fetch라서, reissue 요청 자체가 이 재시도/리다이렉트 로직을 다시 태우지 않는다.
let reissuePromise: Promise<boolean> | null = null;

async function rawFetch(path: string, options?: RequestInit): Promise<Response> {
  return fetch(`${API_BASE}${path}`, buildRequestInit(options));
}

async function reissueAccessToken(): Promise<boolean> {
  if (!reissuePromise) {
    reissuePromise = rawFetch(`${AUTH_PATH_PREFIX}reissue`, { method: "POST" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        reissuePromise = null;
      });
  }
  return reissuePromise;
}

// 재발급-재시도를 apiFetchJson 안에서만 하면, FormData를 직접 apiFetch로 보내는 호출
// (피드 작성/수정, 프로필 이미지 변경 등)은 혜택을 못 받아 accessToken 만료 직후 401로
// 실패한다. 이 계층에서 한 번만 구현해서 apiFetch를 쓰는 모든 호출이 자동으로 적용받게
// 한다. body가 FormData/문자열이면 fetch 간에 재사용 가능하므로 그대로 재시도해도 안전하다.
//
// retriedAfterReissue는 재귀 재시도를 한 번으로 막기 위한 내부 상태라서 공개 시그니처에
// 노출하지 않는다(호출부가 실수로 세 번째 인자를 넘기면 재시도가 스킵될 수 있으므로).
// 공개 진입점인 apiFetch는 (path, options)만 받는다.
async function apiFetchInternal(
  path: string,
  options: RequestInit | undefined,
  retriedAfterReissue: boolean,
): Promise<Response> {
  const res = await rawFetch(path, options);

  if (res.status === 401 && !path.startsWith(AUTH_PATH_PREFIX)) {
    if (!retriedAfterReissue) {
      const reissued = await reissueAccessToken();
      if (reissued) {
        return apiFetchInternal(path, options, true);
      }
    }

    // 여기 도달했다는 건 reissue가 실패했거나(refreshToken도 무효) reissue 성공 후
    // 재시도한 요청조차 또 401이 났다는 뜻 — 어느 쪽이든 실제로 로그아웃된 상태이므로,
    // apiFetchJson뿐 아니라 apiFetch를 직접 쓰는 호출부(FormData 업로드 등)도 여기서
    // 동일하게 세션 정리 + 로그인 리다이렉트를 받는다.
    if (shouldRedirectToLogin(res.status)) {
      clearClientSession();
      window.location.assign("/login");
    }
  }

  return res;
}

export async function apiFetch(path: string, options?: RequestInit): Promise<Response> {
  return apiFetchInternal(path, options, false);
}

export async function apiFetchJson<T = unknown>(path: string, options?: RequestInit): Promise<{ ok: boolean; data?: T; message?: string }> {
  const res = await apiFetch(path, options);
  const json = await res.json().catch(() => ({}));

  if (!res.ok) {
    return { ok: false, message: json.message || "요청에 실패했습니다." };
  }

  return { ok: true, data: json.data, message: json.message };
}
