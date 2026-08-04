export const MOOD_TAGS = [
  { value: "DATE", label: "데이트" },
  { value: "SOLO", label: "혼밥" },
  { value: "GROUP", label: "회식" },
  { value: "NIGHT", label: "야식" },
  { value: "FAMILY", label: "가족" },
  { value: "FRIENDS", label: "친구" },
] as const;

export type MoodTagValue = (typeof MOOD_TAGS)[number]["value"];

export function moodLabel(value: string | null | undefined): string {
  return MOOD_TAGS.find((t) => t.value === value)?.label ?? value ?? "";
}
