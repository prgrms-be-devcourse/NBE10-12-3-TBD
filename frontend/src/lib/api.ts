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
    // 재시도한 요청조차 또 401이 났다는 뜻 — 어느 쪽이든 실제로 로그아웃된 상태다.
    if (shouldRedirectToLogin(res.status)) {
      clearClientSession();
      window.location.assign("/login");

      // window.location.assign은 페이지 이동을 "예약"할 뿐 스크립트 실행을 즉시 멈추지
      // 않는다. 여기서 res를 그대로 반환하면 호출부의 await 다음 줄(alert, 에러 UI 등)이
      // 리다이렉트보다 먼저 실행돼 헷갈리는 실패 메시지를 한 번 보여주게 된다. 호출부마다
      // "이게 401이었는지" 개별적으로 가드하는 방식은 계속 빠뜨리기 쉬우므로, 아예 이
      // Promise를 resolve하지 않는다 — 곧 페이지 전체가 unload될 것이므로 pending 상태로
      // 영원히 남아도 문제없고, apiFetch/apiFetchJson을 쓰는 모든 호출부가 별도 조치 없이
      // 일관되게 "그 이후 코드가 실행되지 않음"을 보장받는다.
      return new Promise<Response>(() => {});
    }
  }

  return res;
}

/**
 * fetch를 대체하는 기본 진입점. accessToken 만료(401)를 감지하면 자동으로
 * /auth/reissue 재발급을 시도한 뒤 원 요청을 한 번 재시도한다.
 *
 * ⚠️ 이례적인 계약: 보호된 라우트({@link protectedPathPrefixes})에서 재발급까지
 * 최종 실패하면, 이 함수는 로그인 페이지로 리다이렉트(`window.location.assign`)만
 * 예약하고 **반환한 Promise를 절대 resolve/reject하지 않는다.** 페이지 전체가 곧
 * unload되므로 호출부의 이후 코드(에러 alert 등)가 실행되지 않게 하려는 의도적인
 * 동작이다. 따라서 이 함수를 `Promise.all`/`Promise.race`(타임아웃 포함)처럼 "모든
 * 프라미스가 결국 정착한다"고 가정하는 조합기 안에서 쓰면 그 조합기 전체가 함께
 * 멈출 수 있으니 주의할 것.
 */
export async function apiFetch(path: string, options?: RequestInit): Promise<Response> {
  return apiFetchInternal(path, options, false);
}

/**
 * apiFetch 기반의 JSON 응답 헬퍼. apiFetch의 "재발급 최종 실패 시 절대
 * resolve하지 않는다"는 계약을 그대로 물려받는다.
 */
export async function apiFetchJson<T = unknown>(path: string, options?: RequestInit): Promise<{ ok: boolean; data?: T; message?: string }> {
  const res = await apiFetch(path, options);
  const json = await res.json().catch(() => ({}));

  if (!res.ok) {
    return { ok: false, message: json.message || "요청에 실패했습니다." };
  }

  return { ok: true, data: json.data, message: json.message };
}
