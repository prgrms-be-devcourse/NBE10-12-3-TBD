import assert from "node:assert/strict";
import test from "node:test";

import { buildListHref, parseListSearchParams } from "./listNavigation.ts";

test("selected-only 딥링크는 내 리스트 탭으로 해석한다", () => {
  assert.deepEqual(parseListSearchParams(new URLSearchParams("selected=42")), {
    selectedId: 42,
    tab: "my",
  });
});

test("유효한 selected와 tab을 그대로 해석한다", () => {
  assert.deepEqual(
    parseListSearchParams(new URLSearchParams("selected=17&tab=other")),
    { selectedId: 17, tab: "other" },
  );
  assert.deepEqual(
    parseListSearchParams(new URLSearchParams("selected=8&tab=saved")),
    { selectedId: 8, tab: "saved" },
  );
});

test("유효하지 않은 selected와 tab은 안전한 기본값으로 바꾼다", () => {
  assert.deepEqual(
    parseListSearchParams(new URLSearchParams("selected=7&tab=unknown")),
    { selectedId: 7, tab: "my" },
  );

  for (const [query, tab] of [
    ["selected=0&tab=unknown", "my"],
    ["selected=-1&tab=other", "other"],
    ["selected=1.5&tab=saved", "saved"],
    ["selected=abc&tab=other", "other"],
  ] as const) {
    assert.deepEqual(parseListSearchParams(new URLSearchParams(query)), {
      selectedId: null,
      tab,
    });
  }
});

test("리스트 딥링크에 selected와 대상 tab을 함께 생성한다", () => {
  assert.equal(buildListHref(23, "my"), "/lists?selected=23&tab=my");
  assert.equal(buildListHref(23, "saved"), "/lists?selected=23&tab=saved");
  assert.equal(buildListHref(23, "other"), "/lists?selected=23&tab=other");
});
