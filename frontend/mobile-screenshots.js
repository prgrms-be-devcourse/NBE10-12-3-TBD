/* eslint-disable @typescript-eslint/no-require-imports */
/**
 * 모바일 반응형 검증용 폭별 스크린샷 스크립트
 * 사용법: node mobile-screenshots.js [폭1 폭2 ...]
 *   예) node mobile-screenshots.js 390 1280
 * 전제: http://localhost:3000 에 프론트 dev 서버 실행 중 (pnpm dev)
 */
const puppeteer = require("puppeteer");
const path = require("path");
const fs = require("fs");

const BASE = "http://localhost:3000";
const DEFAULT_WIDTHS = [360, 390, 768, 1024, 1100, 1280, 1440];
const HEIGHT = 900;
const PAGES = [
  ["feed", "/feed"],
  ["search", "/search"],
  ["lists", "/lists"],
  ["recommend", "/recommend"],
  ["profile", "/profile"],
  ["login", "/login"],
  ["lists-create", "/lists/create"],
  ["feed-write", "/feed/write"],
];

(async () => {
  const widths = process.argv.slice(2).map(Number).filter((w) => w > 0);
  const targets = widths.length > 0 ? widths : DEFAULT_WIDTHS;
  const outRoot = path.join(__dirname, "mobile-shots");

  const browser = await puppeteer.launch({
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox"],
  });

  for (const width of targets) {
    const dir = path.join(outRoot, String(width));
    fs.mkdirSync(dir, { recursive: true });

    const page = await browser.newPage();
    await page.setViewport({ width, height: HEIGHT, hasTouch: true });

    // 백엔드가 켜져 있으면 더미 토큰이 401을 받아 클라이언트가 /login으로 튕기므로,
    // API 응답을 빈 데이터 200으로 가로챈다 (레이아웃 검증 목적)
    await page.setRequestInterception(true);
    page.on("request", (req) => {
      const url = req.url();
      if (!url.includes("/api/v1/")) {
        req.continue();
        return;
      }

      // 크로스 오리진(localhost:8080)이라 CORS 헤더가 없으면 브라우저가 차단함
      const corsHeaders = {
        "Access-Control-Allow-Origin": "http://localhost:3000",
        "Access-Control-Allow-Credentials": "true",
        "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
        "Access-Control-Allow-Headers": "*",
      };

      if (req.method() === "OPTIONS") {
        req.respond({ status: 204, headers: corsHeaders });
        return;
      }

      let data = [];
      if (url.includes("/feeds")) {
        data = { feeds: [], totalPages: 0, totalElements: 0, page: 0, size: 10 };
      } else if (url.includes("/lists") || url.includes("restaurant_lists")) {
        data = { lists: [], totalPages: 0, totalElements: 0 };
      } else if (url.includes("/recommend")) {
        data = { recommendations: [] };
      } else if (url.includes("/users")) {
        data = {
          userId: 1,
          nickname: "테스터",
          profileImage: null,
          email: "tester@example.com",
          postCount: 0,
          followerCount: 0,
          followingCount: 0,
        };
      }

      req.respond({
        status: 200,
        contentType: "application/json",
        headers: corsHeaders,
        body: JSON.stringify({ success: true, data, message: "" }),
      });
    });

    // 미들웨어 통과용 더미 쿠키 + 클라이언트 로그인 상태
    await page.setCookie({ name: "accessToken", value: "dummy", domain: "localhost" });
    await page.goto(BASE + "/login", { waitUntil: "networkidle2", timeout: 15000 });
    await page.evaluate(() => {
      localStorage.setItem("isLoggedIn", "true");
      localStorage.setItem(
        "user",
        JSON.stringify({
          userId: 1,
          nickname: "테스터",
          profileImage: null,
          email: "tester@example.com",
        }),
      );
    });

    for (const [name, url] of PAGES) {
      try {
        await page.goto(BASE + url, { waitUntil: "networkidle2", timeout: 15000 });
        await new Promise((r) => setTimeout(r, 1500)); // 하이드레이션 대기
        await page.screenshot({ path: path.join(dir, `${name}.png`), fullPage: true });
        console.log(`[${width}px] ${name} 캡처 완료`);
      } catch (err) {
        console.error(`[${width}px] ${name} 캡처 실패:`, err.message);
      }
    }
    await page.close();
  }

  await browser.close();
  console.log(`저장 위치: ${outRoot}`);
})();
