import assert from "node:assert/strict";
import test from "node:test";

import { apiFetch } from "./api.ts";

// apiFetch의 리다이렉트 분기는 window/localStorage가 있어야 동작하므로(SSR에서는
// 아무 것도 하지 않는다), 테스트에서 그 최소 형태만 흉내낸다.
function stubBrowserGlobals(pathname: string) {
  const assignedUrls: string[] = [];
  const removedKeys: string[] = [];
  const globalRecord = globalThis as unknown as Record<string, unknown>;

  globalRecord.window = {
    location: {
      pathname,
      assign: (url: string) => {
        assignedUrls.push(url);
      },
    },
    dispatchEvent: () => true,
  };

  globalRecord.localStorage = {
    removeItem: (key: string) => {
      removedKeys.push(key);
    },
  };

  return {
    assignedUrls,
    removedKeys,
    restore: () => {
      delete globalRecord.window;
      delete globalRecord.localStorage;
    },
  };
}

function stubUnauthorizedFetch() {
  const original = globalThis.fetch;
  globalThis.fetch = (async () =>
    new Response(JSON.stringify({ message: "unauthorized" }), {
      status: 401,
    })) as typeof fetch;
  return () => {
    globalThis.fetch = original;
  };
}

test("보호된 라우트에서 재발급까지 실패하면 로그인으로 리다이렉트하고 apiFetch는 resolve되지 않는다", async () => {
  const restoreFetch = stubUnauthorizedFetch();
  const browser = stubBrowserGlobals("/feed");

  try {
    let settled = false;
    const pending = apiFetch("/api/v1/feeds").then(() => {
      settled = true;
    });

    // apiFetch가 실제로 영원히 pending인지는 "끝까지 기다려도 안 끝난다"를 직접
    // 증명할 수 없으므로, 충분히 여유 있는 타임아웃과의 race로 그 시점까지
    // resolve되지 않았음을 확인한다.
    const timedOut = Symbol("timeout");
    const result = await Promise.race([
      pending,
      new Promise((resolve) => setTimeout(() => resolve(timedOut), 50)),
    ]);

    assert.equal(result, timedOut, "apiFetch는 리다이렉트 후 resolve되면 안 된다");
    assert.equal(settled, false);
    assert.deepEqual(browser.assignedUrls, ["/login"]);
    assert.ok(browser.removedKeys.includes("user"));
  } finally {
    restoreFetch();
    browser.restore();
  }
});

test("보호되지 않은 라우트(/login)에서는 401이어도 리다이렉트하지 않고 응답을 그대로 반환한다", async () => {
  const restoreFetch = stubUnauthorizedFetch();
  const browser = stubBrowserGlobals("/login");

  try {
    const res = await apiFetch("/api/v1/feeds");

    assert.equal(res.status, 401);
    assert.deepEqual(browser.assignedUrls, []);
  } finally {
    restoreFetch();
    browser.restore();
  }
});
