"use client";

import { ReactNode, useRef, useState } from "react";

/**
 * 결과 머리글 + 식당 1개가 온전히 보이는 최소 높이(px).
 * 스펙: 시트는 이 높이 이하로 낮아질 수 없다.
 */
const MIN_HEIGHT = 200;

interface BottomSheetProps {
  /** 스냅 높이(화면 높이 대비 비율, 오름차순). 예: [0.2, 0.5, 0.85] */
  snaps: number[];
  /** 현재 스냅 인덱스 (부모가 상태를 관리) */
  snap: number;
  /** 드래그 종료 후 스냅 변경 요청 */
  onSnapChange: (index: number) => void;
  children: ReactNode;
}

export default function BottomSheet({
  snaps,
  snap,
  onSnapChange,
  children,
}: BottomSheetProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const draggingRef = useRef(false);
  const startYRef = useRef(0);
  const startHeightRef = useRef(0);
  /** 드래그 중 실시간 높이(px). null이면 드래그 아님 */
  const [dragHeight, setDragHeight] = useState<number | null>(null);

  const maxRatio = Math.max(...snaps);

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    draggingRef.current = true;
    startYRef.current = e.clientY;
    startHeightRef.current =
      containerRef.current?.getBoundingClientRect().height ?? MIN_HEIGHT;
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!draggingRef.current) return;

    const delta = startYRef.current - e.clientY; // 위로 올리면 양수
    const maxHeight = maxRatio * window.innerHeight;
    const next = Math.min(maxHeight, Math.max(MIN_HEIGHT, startHeightRef.current + delta));
    setDragHeight(next);
  };

  const finishDrag = () => {
    if (!draggingRef.current) return;
    draggingRef.current = false;

    if (dragHeight !== null) {
      // 가장 가까운 스냅으로 흡착
      let nearest = 0;
      let bestDistance = Infinity;
      snaps.forEach((ratio, index) => {
        const height = Math.max(MIN_HEIGHT, ratio * window.innerHeight);
        const distance = Math.abs(height - dragHeight);
        if (distance < bestDistance) {
          bestDistance = distance;
          nearest = index;
        }
      });
      onSnapChange(nearest);
    }
    setDragHeight(null);
  };

  return (
    <div
      ref={containerRef}
      style={{
        height:
          dragHeight !== null
            ? `${dragHeight}px`
            : `max(${MIN_HEIGHT}px, calc(${snaps[snap]} * 100dvh))`,
      }}
      className={`fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-30 flex flex-col overflow-hidden rounded-t-2xl border-t border-hairline-soft bg-surface shadow-[0_-4px_20px_rgba(0,0,0,0.08)] lg:hidden ${
        dragHeight !== null ? "" : "transition-[height] duration-300 ease-out"
      }`}
    >
      {/* 드래그 핸들 */}
      <div
        data-sheet-handle
        className="shrink-0 cursor-grab touch-none px-4 pb-2 pt-3 active:cursor-grabbing"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={finishDrag}
        onPointerCancel={finishDrag}
      >
        <div className="mx-auto h-1.5 w-12 rounded-full bg-hairline" />
      </div>

      {/* 콘텐츠 (스크롤은 children이 관리) */}
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {children}
      </div>
    </div>
  );
}
