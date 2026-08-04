/**
 * 카카오맵 JS SDK 키.
 * 환경마다 변수명이 달라서(NEXT_PUBLIC_KAKAO_JS_KEY / NEXT_PUBLIC_KAKAO_MAP_JS_KEY)
 * 둘 다 허용한다. 새 코드는 반드시 이 상수를 사용할 것.
 */
export const KAKAO_JS_KEY =
  process.env.NEXT_PUBLIC_KAKAO_JS_KEY ||
  process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

export const KAKAO_KEY_MISSING_MESSAGE =
  "KAKAO_JS_KEY가 설정되지 않았습니다. .env.local을 확인하세요.";
